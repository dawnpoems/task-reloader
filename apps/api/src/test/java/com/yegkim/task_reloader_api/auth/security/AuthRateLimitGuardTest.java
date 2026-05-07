package com.yegkim.task_reloader_api.auth.security;

import com.yegkim.task_reloader_api.auth.exception.AuthException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AuthRateLimitGuard 단위테스트")
class AuthRateLimitGuardTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("rate-limit 비활성화면 IP 제한을 적용하지 않음")
    void checkIpLimit_disabled_returnsNull() {
        AuthRateLimitGuard guard = new AuthRateLimitGuard(
                new InMemoryFixedWindowRateLimiter(fixedClock()),
                new AuthRateLimitConfig(
                        false,
                        30, 60, 5, 300,
                        10, 300, 3, 600,
                        60, 60
                )
        );
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("10.0.0.1");

        RateLimitViolation violation = guard.checkIpLimit("/api/auth/login", request);

        assertThat(violation).isNull();
    }

    @Test
    @DisplayName("login IP 제한 초과 시 violation 반환")
    void checkIpLimit_loginBlocked_returnsViolation() {
        AuthRateLimitGuard guard = new AuthRateLimitGuard(
                new InMemoryFixedWindowRateLimiter(fixedClock()),
                new AuthRateLimitConfig(
                        true,
                        1, 60, 5, 300,
                        10, 300, 3, 600,
                        60, 60
                )
        );
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("10.0.0.2");

        RateLimitViolation first = guard.checkIpLimit("/api/auth/login", request);
        RateLimitViolation second = guard.checkIpLimit("/api/auth/login", request);

        assertThat(first).isNull();
        assertThat(second).isNotNull();
        assertThat(second.code()).isEqualTo("LOGIN_RATE_LIMITED");
        assertThat(second.retryAfterSeconds()).isGreaterThan(0L);
    }

    @Test
    @DisplayName("login IP+email 제한 초과 시 AuthException(429) 발생")
    void enforceLoginIpEmailLimit_blocked_throwsAuthException() {
        AuthRateLimitGuard guard = new AuthRateLimitGuard(
                new InMemoryFixedWindowRateLimiter(fixedClock()),
                new AuthRateLimitConfig(
                        true,
                        30, 60, 1, 300,
                        10, 300, 3, 600,
                        60, 60
                )
        );
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("10.0.0.3");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        guard.enforceLoginIpEmailLimit("user@example.com");

        assertThatThrownBy(() -> guard.enforceLoginIpEmailLimit("user@example.com"))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> {
                    AuthException authEx = (AuthException) ex;
                    assertThat(authEx.getStatus().value()).isEqualTo(429);
                    assertThat(authEx.getCode()).isEqualTo("LOGIN_ACCOUNT_RATE_LIMITED");
                    assertThat(authEx.getRetryAfterSeconds()).isNotNull();
                    assertThat(authEx.getRetryAfterSeconds()).isGreaterThan(0L);
                });
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-05-05T10:00:00Z"), ZoneOffset.UTC);
    }
}
