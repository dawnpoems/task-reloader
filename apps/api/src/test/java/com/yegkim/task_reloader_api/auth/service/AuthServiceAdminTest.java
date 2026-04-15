package com.yegkim.task_reloader_api.auth.service;

import com.yegkim.task_reloader_api.auth.dto.PendingUserResponse;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 관리자 승인 단위테스트")
class AuthServiceAdminTest {

    private static final long ADMIN_ID = 1L;
    private static final long USER_ID = 2L;

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

    private User adminUser;
    private User pendingUser;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .id(ADMIN_ID)
                .email("admin@test.com")
                .passwordHash("hash")
                .role(UserRole.ADMIN)
                .status(UserStatus.APPROVED)
                .createdAt(OffsetDateTime.of(2026, 4, 1, 0, 0, 0, 0, ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.of(2026, 4, 1, 0, 0, 0, 0, ZoneOffset.UTC))
                .build();

        pendingUser = User.builder()
                .id(USER_ID)
                .email("user@test.com")
                .passwordHash("hash")
                .role(UserRole.USER)
                .status(UserStatus.PENDING)
                .createdAt(OffsetDateTime.of(2026, 4, 2, 0, 0, 0, 0, ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.of(2026, 4, 2, 0, 0, 0, 0, ZoneOffset.UTC))
                .build();
    }

    @Test
    @DisplayName("PENDING 사용자 목록 조회 - 관리자만 가능")
    void getPendingUsers_success() {
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(adminUser));
        when(userRepository.findAllByStatusOrderByCreatedAtAsc(UserStatus.PENDING))
                .thenReturn(List.of(pendingUser));

        List<PendingUserResponse> responses = authService.getPendingUsers(ADMIN_ID);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).userId()).isEqualTo(USER_ID);
        assertThat(responses.get(0).status()).isEqualTo(UserStatus.PENDING);
    }

    @Test
    @DisplayName("승인/거절 사용자 목록 조회 - PENDING 제외")
    void getNonPendingUsers_success() {
        User approvedUser = User.builder()
                .id(3L)
                .email("approved@test.com")
                .passwordHash("hash")
                .role(UserRole.USER)
                .status(UserStatus.APPROVED)
                .createdAt(OffsetDateTime.of(2026, 4, 3, 0, 0, 0, 0, ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.of(2026, 4, 3, 0, 0, 0, 0, ZoneOffset.UTC))
                .build();

        User rejectedUser = User.builder()
                .id(4L)
                .email("rejected@test.com")
                .passwordHash("hash")
                .role(UserRole.USER)
                .status(UserStatus.REJECTED)
                .createdAt(OffsetDateTime.of(2026, 4, 4, 0, 0, 0, 0, ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.of(2026, 4, 4, 0, 0, 0, 0, ZoneOffset.UTC))
                .build();

        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(adminUser));
        when(userRepository.findAll()).thenReturn(List.of(rejectedUser, pendingUser, approvedUser));

        List<PendingUserResponse> responses = authService.getNonPendingUsers(ADMIN_ID);

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(PendingUserResponse::userId).containsExactly(3L, 4L);
        assertThat(responses).extracting(PendingUserResponse::status).containsExactly(UserStatus.APPROVED, UserStatus.REJECTED);
    }

    @Test
    @DisplayName("PENDING 사용자 목록 조회 - 일반 사용자는 금지")
    void getPendingUsers_forbiddenWhenNotAdmin() {
        User normalUser = User.builder()
                .id(10L)
                .email("normal@test.com")
                .passwordHash("hash")
                .role(UserRole.USER)
                .status(UserStatus.APPROVED)
                .build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(normalUser));

        assertThatThrownBy(() -> authService.getPendingUsers(10L))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> {
                    AuthException authEx = (AuthException) ex;
                    assertThat(authEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(authEx.getCode()).isEqualTo("ADMIN_ONLY");
                });
    }

    @Test
    @DisplayName("사용자 승인 - PENDING 계정을 APPROVED로 전환")
    void approvePendingUser_success() {
        Instant now = Instant.parse("2026-04-02T10:00:00Z");
        when(clock.instant()).thenReturn(now);
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(pendingUser));

        PendingUserResponse response = authService.approvePendingUser(ADMIN_ID, USER_ID);

        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.status()).isEqualTo(UserStatus.APPROVED);
        assertThat(pendingUser.getStatus()).isEqualTo(UserStatus.APPROVED);
        assertThat(pendingUser.getApprovedBy()).isEqualTo(adminUser);
        assertThat(pendingUser.getApprovedAt()).isEqualTo(OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("사용자 승인 - PENDING이 아니면 충돌")
    void approvePendingUser_conflictWhenNotPending() {
        User approvedUser = User.builder()
                .id(USER_ID)
                .email("user@test.com")
                .passwordHash("hash")
                .role(UserRole.USER)
                .status(UserStatus.APPROVED)
                .build();
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(approvedUser));

        assertThatThrownBy(() -> authService.approvePendingUser(ADMIN_ID, USER_ID))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> {
                    AuthException authEx = (AuthException) ex;
                    assertThat(authEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(authEx.getCode()).isEqualTo("ACCOUNT_NOT_PENDING");
                });
    }

    @Test
    @DisplayName("사용자 거절 - PENDING 계정을 REJECTED로 전환")
    void rejectPendingUser_success() {
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(pendingUser));

        PendingUserResponse response = authService.rejectPendingUser(ADMIN_ID, USER_ID);

        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.status()).isEqualTo(UserStatus.REJECTED);
        assertThat(pendingUser.getStatus()).isEqualTo(UserStatus.REJECTED);
        assertThat(pendingUser.getApprovedBy()).isNull();
        assertThat(pendingUser.getApprovedAt()).isNull();
    }

    @Test
    @DisplayName("사용자 거절 - PENDING이 아니면 충돌")
    void rejectPendingUser_conflictWhenNotPending() {
        User rejectedUser = User.builder()
                .id(USER_ID)
                .email("user@test.com")
                .passwordHash("hash")
                .role(UserRole.USER)
                .status(UserStatus.REJECTED)
                .build();
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(rejectedUser));

        assertThatThrownBy(() -> authService.rejectPendingUser(ADMIN_ID, USER_ID))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> {
                    AuthException authEx = (AuthException) ex;
                    assertThat(authEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(authEx.getCode()).isEqualTo("ACCOUNT_NOT_PENDING");
                });
    }
}
