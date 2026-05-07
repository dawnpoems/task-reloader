package com.yegkim.task_reloader_api.auth.security;

import com.yegkim.task_reloader_api.auth.exception.AuthException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class AuthRateLimitGuard {

    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String SIGNUP_PATH = "/api/auth/signup";
    private static final String REFRESH_PATH = "/api/auth/refresh";
    private static final String UNKNOWN_CLIENT = "unknown";
    private static final String KEY_PREFIX = "auth-rate-limit";

    private final InMemoryFixedWindowRateLimiter rateLimiter;
    private final AuthRateLimitConfig authRateLimitConfig;

    public RateLimitViolation checkIpLimit(String path, HttpServletRequest request) {
        if (!authRateLimitConfig.enabled()) {
            return null;
        }

        String clientIp = normalizeClientIp(resolveClientIp(request));

        return switch (path) {
            case LOGIN_PATH -> evaluate(
                    "login-ip",
                    clientIp,
                    authRateLimitConfig.loginIpLimit(),
                    authRateLimitConfig.loginIpWindowSeconds(),
                    "LOGIN_RATE_LIMITED",
                    "로그인 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."
            );
            case SIGNUP_PATH -> evaluate(
                    "signup-ip",
                    clientIp,
                    authRateLimitConfig.signupIpLimit(),
                    authRateLimitConfig.signupIpWindowSeconds(),
                    "SIGNUP_RATE_LIMITED",
                    "회원가입 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."
            );
            case REFRESH_PATH -> evaluate(
                    "refresh-ip",
                    clientIp,
                    authRateLimitConfig.refreshIpLimit(),
                    authRateLimitConfig.refreshIpWindowSeconds(),
                    "REFRESH_RATE_LIMITED",
                    "세션 갱신 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."
            );
            default -> null;
        };
    }

    public void enforceLoginIpEmailLimit(String email) {
        if (!authRateLimitConfig.enabled()) {
            return;
        }

        String clientIp = normalizeClientIp(resolveCurrentRequestClientIp());
        String normalizedEmail = normalizeEmail(email);
        String keyValue = clientIp + "|" + normalizedEmail;

        RateLimitViolation violation = evaluate(
                "login-ip-email",
                keyValue,
                authRateLimitConfig.loginIpEmailLimit(),
                authRateLimitConfig.loginIpEmailWindowSeconds(),
                "LOGIN_ACCOUNT_RATE_LIMITED",
                "동일 계정 로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요."
        );
        throwIfViolation(violation);
    }

    public void enforceSignupIpEmailLimit(String email) {
        if (!authRateLimitConfig.enabled()) {
            return;
        }

        String clientIp = normalizeClientIp(resolveCurrentRequestClientIp());
        String normalizedEmail = normalizeEmail(email);
        String keyValue = clientIp + "|" + normalizedEmail;

        RateLimitViolation violation = evaluate(
                "signup-ip-email",
                keyValue,
                authRateLimitConfig.signupIpEmailLimit(),
                authRateLimitConfig.signupIpEmailWindowSeconds(),
                "SIGNUP_ACCOUNT_RATE_LIMITED",
                "동일 이메일 가입 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요."
        );
        throwIfViolation(violation);
    }

    private void throwIfViolation(RateLimitViolation violation) {
        if (violation == null) {
            return;
        }
        throw new AuthException(
                HttpStatus.TOO_MANY_REQUESTS,
                violation.code(),
                violation.message(),
                violation.retryAfterSeconds()
        );
    }

    private RateLimitViolation evaluate(
            String scope,
            String keyValue,
            int limit,
            long windowSeconds,
            String code,
            String message
    ) {
        String key = buildKey(scope, keyValue);
        RateLimitResult result = rateLimiter.tryConsume(key, limit, windowSeconds);
        if (result.allowed()) {
            return null;
        }
        return new RateLimitViolation(code, message, result.retryAfterSeconds());
    }

    private String buildKey(String scope, String keyValue) {
        return KEY_PREFIX + ":" + scope + ":" + keyValue;
    }

    private String resolveCurrentRequestClientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return UNKNOWN_CLIENT;
        }
        return resolveClientIp(attributes.getRequest());
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN_CLIENT;
        }

        String xForwardedFor = firstForwardedIp(request.getHeader("X-Forwarded-For"));
        if (hasText(xForwardedFor)) {
            return xForwardedFor;
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (hasText(xRealIp)) {
            return xRealIp.trim();
        }

        if (hasText(request.getRemoteAddr())) {
            return request.getRemoteAddr().trim();
        }

        return UNKNOWN_CLIENT;
    }

    private String firstForwardedIp(String headerValue) {
        if (!hasText(headerValue)) {
            return null;
        }
        String[] parts = headerValue.split(",");
        for (String part : parts) {
            if (hasText(part)) {
                return part.trim();
            }
        }
        return null;
    }

    private String normalizeClientIp(String value) {
        if (!hasText(value)) {
            return UNKNOWN_CLIENT;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeEmail(String value) {
        if (!hasText(value)) {
            return "unknown-email";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
