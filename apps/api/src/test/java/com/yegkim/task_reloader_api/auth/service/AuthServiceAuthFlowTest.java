package com.yegkim.task_reloader_api.auth.service;

import com.yegkim.task_reloader_api.auth.dto.LoginRequest;
import com.yegkim.task_reloader_api.auth.dto.SignupRequest;
import com.yegkim.task_reloader_api.auth.entity.RefreshToken;
import com.yegkim.task_reloader_api.auth.entity.User;
import com.yegkim.task_reloader_api.auth.entity.UserRole;
import com.yegkim.task_reloader_api.auth.entity.UserStatus;
import com.yegkim.task_reloader_api.auth.exception.AuthException;
import com.yegkim.task_reloader_api.auth.jwt.JwtTokenProvider;
import com.yegkim.task_reloader_api.auth.repository.RefreshTokenRepository;
import com.yegkim.task_reloader_api.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 인증 흐름 단위테스트")
class AuthServiceAuthFlowTest {

    private static final long ACCESS_TTL_SECONDS = 900L;
    private static final long REFRESH_TTL_SECONDS = 1_209_600L;
    private static final Instant FIXED_NOW = Instant.parse("2026-04-02T00:00:00Z");

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private Clock clock;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "accessTokenTtlSeconds", ACCESS_TTL_SECONDS);
        ReflectionTestUtils.setField(authService, "refreshTokenTtlSeconds", REFRESH_TTL_SECONDS);
        ReflectionTestUtils.setField(authService, "loginLockThreshold", 5);
        ReflectionTestUtils.setField(authService, "loginLockBaseSeconds", 60L);
        ReflectionTestUtils.setField(authService, "loginLockMaxSeconds", 3600L);
        ReflectionTestUtils.setField(authService, "loginLockResetWindowSeconds", 900L);
        lenient().when(clock.instant()).thenReturn(FIXED_NOW);
        lenient().when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("회원가입 성공 - 이메일 정규화 + PENDING 계정 생성")
    void signup_success() {
        SignupRequest request = new SignupRequest("  User@Example.com ", "Password1");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Password1")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return User.builder()
                    .id(10L)
                    .email(user.getEmail())
                    .passwordHash(user.getPasswordHash())
                    .role(user.getRole())
                    .status(user.getStatus())
                    .build();
        });

        var response = authService.signup(request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.status()).isEqualTo(UserStatus.PENDING);
        verify(userRepository).findByEmail("user@example.com");
    }

    @Test
    @DisplayName("회원가입 실패 - 이미 존재하는 이메일")
    void signup_duplicateEmail_conflict() {
        SignupRequest request = new SignupRequest("user@example.com", "Password1");
        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(user(1L, "user@example.com", UserRole.USER, UserStatus.APPROVED)));

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> {
                    AuthException authEx = (AuthException) ex;
                    assertThat(authEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(authEx.getCode()).isEqualTo("EMAIL_ALREADY_EXISTS");
                });
    }

    @Test
    @DisplayName("로그인 성공 - APPROVED 계정에 access/refresh 발급")
    void login_success() {
        LoginRequest request = new LoginRequest(" User@Example.com ", "Password1");
        User user = user(11L, "user@example.com", UserRole.USER, UserStatus.APPROVED);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password1", "hash-11")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(11L, UserRole.USER)).thenReturn("access-token");

        AuthService.LoginResult result = authService.login(request);

        assertThat(result.response().tokenType()).isEqualTo("Bearer");
        assertThat(result.response().accessToken()).isEqualTo("access-token");
        assertThat(result.response().expiresInSeconds()).isEqualTo(ACCESS_TTL_SECONDS);
        assertThat(result.response().status()).isEqualTo(UserStatus.APPROVED);
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.refreshTtlSeconds()).isEqualTo(REFRESH_TTL_SECONDS);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken savedRefresh = captor.getValue();
        assertThat(savedRefresh.getUser()).isEqualTo(user);
        assertThat(savedRefresh.getExpiresAt()).isEqualTo(OffsetDateTime.ofInstant(FIXED_NOW, ZoneOffset.UTC).plusSeconds(REFRESH_TTL_SECONDS));
        assertThat(savedRefresh.getTokenHash()).isEqualTo(hash(result.refreshToken()));
    }

    @Test
    @DisplayName("로그인 실패 - 자격 증명 불일치")
    void login_invalidCredentials_unauthorized() {
        LoginRequest request = new LoginRequest("user@example.com", "WrongPassword");
        User user = user(11L, "user@example.com", UserRole.USER, UserStatus.APPROVED);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword", "hash-11")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> {
                    AuthException authEx = (AuthException) ex;
                    assertThat(authEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(authEx.getCode()).isEqualTo("INVALID_CREDENTIALS");
                });
    }

    @Test
    @DisplayName("로그인 실패 - PENDING 계정")
    void login_pendingAccount_forbidden() {
        LoginRequest request = new LoginRequest("user@example.com", "Password1");
        User pending = user(12L, "user@example.com", UserRole.USER, UserStatus.PENDING);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(pending));
        when(passwordEncoder.matches("Password1", "hash-12")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> {
                    AuthException authEx = (AuthException) ex;
                    assertThat(authEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(authEx.getCode()).isEqualTo("ACCOUNT_PENDING");
                });
    }

    @Test
    @DisplayName("로그인 실패 - REJECTED 계정")
    void login_rejectedAccount_forbidden() {
        LoginRequest request = new LoginRequest("user@example.com", "Password1");
        User rejected = user(13L, "user@example.com", UserRole.USER, UserStatus.REJECTED);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(rejected));
        when(passwordEncoder.matches("Password1", "hash-13")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> {
                    AuthException authEx = (AuthException) ex;
                    assertThat(authEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(authEx.getCode()).isEqualTo("ACCOUNT_REJECTED");
                });
    }

    @Test
    @DisplayName("로그인 실패 누적 - 임계치 이후 계정 잠금")
    void login_failedAttempts_lockAfterThreshold() {
        LoginRequest request = new LoginRequest("user@example.com", "WrongPassword");
        User user = user(14L, "user@example.com", UserRole.USER, UserStatus.APPROVED);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword", "hash-14")).thenReturn(false);

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> {
                        AuthException authEx = (AuthException) ex;
                        assertThat(authEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                        assertThat(authEx.getCode()).isEqualTo("INVALID_CREDENTIALS");
                    });
        }

        assertThat(user.getFailedLoginCount()).isEqualTo(5);
        assertThat(user.getLockedUntil()).isEqualTo(OffsetDateTime.ofInstant(FIXED_NOW, ZoneOffset.UTC).plusSeconds(60));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> {
                    AuthException authEx = (AuthException) ex;
                    assertThat(authEx.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(authEx.getCode()).isEqualTo("ACCOUNT_LOCKED");
                });
    }

    @Test
    @DisplayName("로그인 성공 시 실패 누적/잠금 상태 초기화")
    void login_success_clearsFailureState() {
        LoginRequest request = new LoginRequest("user@example.com", "Password1");
        User user = User.builder()
                .id(15L)
                .email("user@example.com")
                .passwordHash("hash-15")
                .role(UserRole.USER)
                .status(UserStatus.APPROVED)
                .failedLoginCount(4)
                .lastFailedLoginAt(OffsetDateTime.ofInstant(FIXED_NOW.minusSeconds(30), ZoneOffset.UTC))
                .build();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password1", "hash-15")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(15L, UserRole.USER)).thenReturn("access-token");

        AuthService.LoginResult result = authService.login(request);

        assertThat(result.response().accessToken()).isEqualTo("access-token");
        assertThat(user.getFailedLoginCount()).isEqualTo(0);
        assertThat(user.getLastFailedLoginAt()).isNull();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("로그인 실패 간격이 reset-window를 넘으면 카운터 초기화 후 재누적")
    void login_failedAfterResetWindow_resetsCounter() {
        LoginRequest request = new LoginRequest("user@example.com", "WrongPassword");
        User user = User.builder()
                .id(16L)
                .email("user@example.com")
                .passwordHash("hash-16")
                .role(UserRole.USER)
                .status(UserStatus.APPROVED)
                .failedLoginCount(4)
                .lastFailedLoginAt(OffsetDateTime.ofInstant(FIXED_NOW.minusSeconds(901), ZoneOffset.UTC))
                .build();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword", "hash-16")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> {
                    AuthException authEx = (AuthException) ex;
                    assertThat(authEx.getCode()).isEqualTo("INVALID_CREDENTIALS");
                });

        assertThat(user.getFailedLoginCount()).isEqualTo(1);
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getLastFailedLoginAt()).isEqualTo(OffsetDateTime.ofInstant(FIXED_NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("잠금 상태 계정은 비밀번호 검증 전에 차단")
    void login_lockedAccount_blocksBeforePasswordCheck() {
        LoginRequest request = new LoginRequest("user@example.com", "Password1");
        User user = User.builder()
                .id(17L)
                .email("user@example.com")
                .passwordHash("hash-17")
                .role(UserRole.USER)
                .status(UserStatus.APPROVED)
                .failedLoginCount(5)
                .lastFailedLoginAt(OffsetDateTime.ofInstant(FIXED_NOW.minusSeconds(10), ZoneOffset.UTC))
                .lockedUntil(OffsetDateTime.ofInstant(FIXED_NOW.plusSeconds(60), ZoneOffset.UTC))
                .build();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> {
                    AuthException authEx = (AuthException) ex;
                    assertThat(authEx.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(authEx.getCode()).isEqualTo("ACCOUNT_LOCKED");
                });

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    @DisplayName("리프레시 실패 - 토큰 없음")
    void refresh_missingToken_unauthorized() {
        assertThatThrownBy(() -> authService.refresh(" "))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> {
                    AuthException authEx = (AuthException) ex;
                    assertThat(authEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(authEx.getCode()).isEqualTo("REFRESH_TOKEN_MISSING");
                });
    }

    @Test
    @DisplayName("리프레시 실패 - 유효하지 않은 토큰")
    void refresh_invalidToken_unauthorized() {
        String raw = "invalid-refresh-token";
        when(refreshTokenRepository.findByTokenHash(hash(raw))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(raw))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> {
                    AuthException authEx = (AuthException) ex;
                    assertThat(authEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(authEx.getCode()).isEqualTo("INVALID_REFRESH_TOKEN");
                });
    }

    @Test
    @DisplayName("리프레시 실패 - 만료된 토큰은 revoke 처리")
    void refresh_expiredToken_revokeAndFail() {
        User user = user(21L, "user@example.com", UserRole.USER, UserStatus.APPROVED);
        String raw = "expired-refresh-token";
        RefreshToken expired = RefreshToken.builder()
                .id(300L)
                .user(user)
                .tokenHash(hash(raw))
                .expiresAt(OffsetDateTime.ofInstant(FIXED_NOW.minusSeconds(1), ZoneOffset.UTC))
                .build();
        when(refreshTokenRepository.findByTokenHash(hash(raw))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh(raw))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> {
                    AuthException authEx = (AuthException) ex;
                    assertThat(authEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(authEx.getCode()).isEqualTo("REFRESH_TOKEN_EXPIRED");
                });
        assertThat(expired.getRevokedAt()).isEqualTo(OffsetDateTime.ofInstant(FIXED_NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("리프레시 실패 - 재사용 토큰 감지 시 활성 세션 전체 revoke")
    void refresh_reusedToken_revokesAllSessions() {
        User user = user(22L, "user@example.com", UserRole.USER, UserStatus.APPROVED);
        String raw = "reused-refresh-token";

        RefreshToken reused = RefreshToken.builder()
                .id(401L)
                .user(user)
                .tokenHash(hash(raw))
                .expiresAt(OffsetDateTime.ofInstant(FIXED_NOW.plusSeconds(1000), ZoneOffset.UTC))
                .revokedAt(OffsetDateTime.ofInstant(FIXED_NOW.minusSeconds(10), ZoneOffset.UTC))
                .build();
        RefreshToken active1 = RefreshToken.builder()
                .id(402L)
                .user(user)
                .tokenHash("active-1")
                .expiresAt(OffsetDateTime.ofInstant(FIXED_NOW.plusSeconds(1000), ZoneOffset.UTC))
                .build();
        RefreshToken active2 = RefreshToken.builder()
                .id(403L)
                .user(user)
                .tokenHash("active-2")
                .expiresAt(OffsetDateTime.ofInstant(FIXED_NOW.plusSeconds(1000), ZoneOffset.UTC))
                .build();

        when(refreshTokenRepository.findByTokenHash(hash(raw))).thenReturn(Optional.of(reused));
        when(refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(22L)).thenReturn(List.of(active1, active2));

        assertThatThrownBy(() -> authService.refresh(raw))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> {
                    AuthException authEx = (AuthException) ex;
                    assertThat(authEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(authEx.getCode()).isEqualTo("REFRESH_TOKEN_REUSED");
                });

        OffsetDateTime expected = OffsetDateTime.ofInstant(FIXED_NOW, ZoneOffset.UTC);
        assertThat(active1.getRevokedAt()).isEqualTo(expected);
        assertThat(active2.getRevokedAt()).isEqualTo(expected);
    }

    @Test
    @DisplayName("리프레시 실패 - REJECTED 계정은 세션 전체 revoke 후 차단")
    void refresh_rejectedUser_revokesAllSessionsAndFails() {
        User rejected = user(24L, "rejected@example.com", UserRole.USER, UserStatus.REJECTED);
        String raw = "rejected-refresh-token";

        RefreshToken current = RefreshToken.builder()
                .id(410L)
                .user(rejected)
                .tokenHash(hash(raw))
                .expiresAt(OffsetDateTime.ofInstant(FIXED_NOW.plusSeconds(1000), ZoneOffset.UTC))
                .build();
        RefreshToken otherActive = RefreshToken.builder()
                .id(411L)
                .user(rejected)
                .tokenHash("active-other")
                .expiresAt(OffsetDateTime.ofInstant(FIXED_NOW.plusSeconds(1000), ZoneOffset.UTC))
                .build();

        when(refreshTokenRepository.findByTokenHash(hash(raw))).thenReturn(Optional.of(current));
        when(refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(24L)).thenReturn(List.of(current, otherActive));

        assertThatThrownBy(() -> authService.refresh(raw))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> {
                    AuthException authEx = (AuthException) ex;
                    assertThat(authEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(authEx.getCode()).isEqualTo("ACCOUNT_REJECTED");
                });

        OffsetDateTime expected = OffsetDateTime.ofInstant(FIXED_NOW, ZoneOffset.UTC);
        assertThat(current.getRevokedAt()).isEqualTo(expected);
        assertThat(otherActive.getRevokedAt()).isEqualTo(expected);
        assertThat(current.getLastUsedAt()).isNull();
        verify(jwtTokenProvider, never()).generateAccessToken(any(), any());
    }

    @Test
    @DisplayName("리프레시 성공 - 기존 토큰 사용표시+revoke 후 새 토큰 발급")
    void refresh_success_rotatesToken() {
        User user = user(23L, "user@example.com", UserRole.USER, UserStatus.APPROVED);
        String raw = "refresh-token";
        RefreshToken oldToken = RefreshToken.builder()
                .id(500L)
                .user(user)
                .tokenHash(hash(raw))
                .expiresAt(OffsetDateTime.ofInstant(FIXED_NOW.plusSeconds(5000), ZoneOffset.UTC))
                .build();
        when(refreshTokenRepository.findByTokenHash(hash(raw))).thenReturn(Optional.of(oldToken));
        when(jwtTokenProvider.generateAccessToken(23L, UserRole.USER)).thenReturn("new-access-token");

        AuthService.RefreshResult result = authService.refresh(raw);

        OffsetDateTime expected = OffsetDateTime.ofInstant(FIXED_NOW, ZoneOffset.UTC);
        assertThat(oldToken.getLastUsedAt()).isEqualTo(expected);
        assertThat(oldToken.getRevokedAt()).isEqualTo(expected);

        assertThat(result.response().tokenType()).isEqualTo("Bearer");
        assertThat(result.response().accessToken()).isEqualTo("new-access-token");
        assertThat(result.response().expiresInSeconds()).isEqualTo(ACCESS_TTL_SECONDS);
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.refreshTtlSeconds()).isEqualTo(REFRESH_TTL_SECONDS);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken newlySaved = captor.getValue();
        assertThat(newlySaved.getUser()).isEqualTo(user);
        assertThat(newlySaved.getTokenHash()).isEqualTo(hash(result.refreshToken()));
    }

    @Test
    @DisplayName("로그아웃 - refresh token이 있으면 revoke")
    void logout_revokesRefreshToken() {
        User user = user(31L, "user@example.com", UserRole.USER, UserStatus.APPROVED);
        String raw = "logout-refresh";
        RefreshToken token = RefreshToken.builder()
                .id(600L)
                .user(user)
                .tokenHash(hash(raw))
                .expiresAt(OffsetDateTime.ofInstant(FIXED_NOW.plusSeconds(1000), ZoneOffset.UTC))
                .build();
        when(refreshTokenRepository.findByTokenHash(hash(raw))).thenReturn(Optional.of(token));

        authService.logout(raw);

        assertThat(token.getRevokedAt()).isEqualTo(OffsetDateTime.ofInstant(FIXED_NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("로그아웃 - refresh token이 비어있으면 조회하지 않음")
    void logout_emptyToken_noOp() {
        authService.logout(" ");
        verify(refreshTokenRepository, never()).findByTokenHash(any());
    }

    @Test
    @DisplayName("내 정보 조회 성공")
    void getMe_success() {
        User user = user(41L, "me@example.com", UserRole.USER, UserStatus.APPROVED);
        when(userRepository.findById(41L)).thenReturn(Optional.of(user));

        var response = authService.getMe(41L);

        assertThat(response.userId()).isEqualTo(41L);
        assertThat(response.email()).isEqualTo("me@example.com");
        assertThat(response.role()).isEqualTo(UserRole.USER);
        assertThat(response.status()).isEqualTo(UserStatus.APPROVED);
    }

    @Test
    @DisplayName("내 정보 조회 - 존재하지 않으면 USER_NOT_FOUND")
    void getMe_notFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getMe(999L))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> {
                    AuthException authEx = (AuthException) ex;
                    assertThat(authEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(authEx.getCode()).isEqualTo("USER_NOT_FOUND");
                });
    }

    private User user(Long id, String email, UserRole role, UserStatus status) {
        return User.builder()
                .id(id)
                .email(email)
                .passwordHash("hash-" + id)
                .role(role)
                .status(status)
                .build();
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
