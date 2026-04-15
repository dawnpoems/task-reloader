package com.yegkim.task_reloader_api.auth.service;

import com.yegkim.task_reloader_api.auth.dto.AccessTokenResponse;
import com.yegkim.task_reloader_api.auth.dto.LoginRequest;
import com.yegkim.task_reloader_api.auth.dto.LoginResponse;
import com.yegkim.task_reloader_api.auth.dto.MeResponse;
import com.yegkim.task_reloader_api.auth.dto.PendingUserResponse;
import com.yegkim.task_reloader_api.auth.dto.SignupRequest;
import com.yegkim.task_reloader_api.auth.dto.SignupResponse;
import com.yegkim.task_reloader_api.auth.entity.RefreshToken;
import com.yegkim.task_reloader_api.auth.entity.User;
import com.yegkim.task_reloader_api.auth.entity.UserRole;
import com.yegkim.task_reloader_api.auth.entity.UserStatus;
import com.yegkim.task_reloader_api.auth.exception.AuthException;
import com.yegkim.task_reloader_api.auth.jwt.JwtTokenProvider;
import com.yegkim.task_reloader_api.auth.repository.RefreshTokenRepository;
import com.yegkim.task_reloader_api.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${auth.jwt.access-token-ttl-seconds}")
    private long accessTokenTtlSeconds;

    @Value("${auth.jwt.refresh-token-ttl-seconds}")
    private long refreshTokenTtlSeconds;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.findByEmail(email).isPresent()) {
            throw new AuthException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "이미 사용 중인 이메일입니다.");
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.USER)
                .status(UserStatus.PENDING)
                .build();

        User saved = userRepository.save(user);
        return new SignupResponse(saved.getId(), saved.getEmail(), saved.getStatus());
    }

    @Transactional
    public LoginResult login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email)
                .orElseThrow(this::invalidCredentials);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        if (user.getStatus() == UserStatus.PENDING) {
            throw new AuthException(HttpStatus.FORBIDDEN, "ACCOUNT_PENDING", "관리자 승인 대기 중인 계정입니다.");
        }
        if (user.getStatus() == UserStatus.REJECTED) {
            throw new AuthException(HttpStatus.FORBIDDEN, "ACCOUNT_REJECTED", "승인 거부된 계정입니다.");
        }

        TokenBundle bundle = issueTokens(user);
        LoginResponse response = new LoginResponse(
                TOKEN_TYPE,
                bundle.accessToken(),
                accessTokenTtlSeconds,
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getStatus()
        );
        return new LoginResult(response, bundle.refreshToken(), refreshTokenTtlSeconds);
    }

    @Transactional(noRollbackFor = AuthException.class)
    public RefreshResult refresh(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_MISSING", "리프레시 토큰이 없습니다.");
        }

        String tokenHash = hashToken(refreshTokenValue);
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "유효하지 않은 리프레시 토큰입니다."));

        OffsetDateTime now = nowUtc();
        if (refreshToken.isRevoked()) {
            revokeAllUserTokens(refreshToken.getUser().getId(), now, "refresh_token_reused");
            throw new AuthException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_REUSED", "리프레시 토큰이 재사용되어 세션을 종료했습니다. 다시 로그인해 주세요.");
        }

        if (refreshToken.getExpiresAt().isBefore(now)) {
            refreshToken.revoke(now);
            throw new AuthException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_EXPIRED", "리프레시 토큰이 만료되었습니다. 다시 로그인해 주세요.");
        }

        refreshToken.markUsed(now);
        refreshToken.revoke(now);

        TokenBundle bundle = issueTokens(refreshToken.getUser());
        AccessTokenResponse response = new AccessTokenResponse(TOKEN_TYPE, bundle.accessToken(), accessTokenTtlSeconds);
        return new RefreshResult(response, bundle.refreshToken(), refreshTokenTtlSeconds);
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            return;
        }
        String tokenHash = hashToken(refreshTokenValue);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            if (!token.isRevoked()) {
                token.revoke(nowUtc());
            }
        });
    }

    public MeResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자 정보를 찾을 수 없습니다."));
        return new MeResponse(user.getId(), user.getEmail(), user.getRole(), user.getStatus());
    }

    public List<PendingUserResponse> getPendingUsers(Long adminUserId) {
        validateAdminUser(adminUserId);
        return userRepository.findAllByStatusOrderByCreatedAtAsc(UserStatus.PENDING).stream()
                .map(this::toPendingUserResponse)
                .toList();
    }

    public List<PendingUserResponse> getNonPendingUsers(Long adminUserId) {
        validateAdminUser(adminUserId);
        return userRepository.findAll().stream()
                .filter(user -> user.getStatus() != UserStatus.PENDING)
                .sorted(java.util.Comparator.comparing(User::getCreatedAt))
                .map(this::toPendingUserResponse)
                .toList();
    }

    @Transactional
    public PendingUserResponse updateNonPendingUserStatus(Long adminUserId, Long targetUserId, UserStatus targetStatus) {
        User adminUser = validateAdminUser(adminUserId);
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자 정보를 찾을 수 없습니다."));

        if (targetUser.getRole() == UserRole.ADMIN) {
            throw new AuthException(HttpStatus.CONFLICT, "ADMIN_STATUS_IMMUTABLE", "관리자 계정의 상태는 변경할 수 없습니다.");
        }
        if (targetUser.getStatus() == UserStatus.PENDING) {
            throw new AuthException(HttpStatus.CONFLICT, "ACCOUNT_PENDING_ONLY", "PENDING 계정은 승인/거절 API로 처리해 주세요.");
        }
        if (targetStatus == UserStatus.PENDING) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_TARGET_STATUS", "상태는 APPROVED 또는 REJECTED만 허용됩니다.");
        }

        UserStatus previousStatus = targetUser.getStatus();
        if (previousStatus == targetStatus) {
            return toPendingUserResponse(targetUser);
        }

        OffsetDateTime now = nowUtc();
        if (targetStatus == UserStatus.APPROVED) {
            targetUser.approve(adminUser, now);
        } else {
            targetUser.reject();
            revokeAllUserTokens(targetUser.getId(), now, "admin_status_rejected");
        }

        log.info(
                "Non-pending user status changed adminUserId={} targetUserId={} from={} to={}",
                adminUserId,
                targetUserId,
                previousStatus,
                targetStatus
        );
        return toPendingUserResponse(targetUser);
    }

    @Transactional
    public PendingUserResponse approvePendingUser(Long adminUserId, Long targetUserId) {
        User adminUser = validateAdminUser(adminUserId);
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자 정보를 찾을 수 없습니다."));

        if (targetUser.getStatus() != UserStatus.PENDING) {
            throw new AuthException(HttpStatus.CONFLICT, "ACCOUNT_NOT_PENDING", "승인 대기 상태 계정만 승인할 수 있습니다.");
        }

        targetUser.approve(adminUser, nowUtc());
        log.info("User approved adminUserId={} targetUserId={}", adminUserId, targetUserId);
        return toPendingUserResponse(targetUser);
    }

    @Transactional
    public PendingUserResponse rejectPendingUser(Long adminUserId, Long targetUserId) {
        validateAdminUser(adminUserId);
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자 정보를 찾을 수 없습니다."));

        if (targetUser.getStatus() != UserStatus.PENDING) {
            throw new AuthException(HttpStatus.CONFLICT, "ACCOUNT_NOT_PENDING", "승인 대기 상태 계정만 거절할 수 있습니다.");
        }

        targetUser.reject();
        log.info("User rejected adminUserId={} targetUserId={}", adminUserId, targetUserId);
        return toPendingUserResponse(targetUser);
    }

    private User validateAdminUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자 정보를 찾을 수 없습니다."));
        if (user.getRole() != UserRole.ADMIN) {
            throw new AuthException(HttpStatus.FORBIDDEN, "ADMIN_ONLY", "관리자 권한이 필요합니다.");
        }
        return user;
    }

    private PendingUserResponse toPendingUserResponse(User user) {
        return new PendingUserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }

    private TokenBundle issueTokens(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getRole());
        String refreshTokenValue = generateOpaqueToken();
        String refreshTokenHash = hashToken(refreshTokenValue);
        OffsetDateTime expiresAt = nowUtc().plusSeconds(refreshTokenTtlSeconds);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(refreshTokenHash)
                .expiresAt(expiresAt)
                .build();

        refreshTokenRepository.save(refreshToken);
        return new TokenBundle(accessToken, refreshTokenValue);
    }

    private void revokeAllUserTokens(Long userId, OffsetDateTime now, String reason) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId);
        for (RefreshToken token : activeTokens) {
            token.revoke(now);
        }
        log.warn("User tokens revoked userId={} count={} reason={}", userId, activeTokens.size(), reason);
    }

    private String generateOpaqueToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hashToken(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }

    private OffsetDateTime nowUtc() {
        Instant now = clock.instant();
        return OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private AuthException invalidCredentials() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.");
    }

    public record LoginResult(LoginResponse response, String refreshToken, long refreshTtlSeconds) {
    }

    public record RefreshResult(AccessTokenResponse response, String refreshToken, long refreshTtlSeconds) {
    }

    private record TokenBundle(String accessToken, String refreshToken) {
    }
}
