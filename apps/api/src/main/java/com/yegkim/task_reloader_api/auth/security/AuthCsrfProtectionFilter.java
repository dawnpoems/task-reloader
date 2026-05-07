package com.yegkim.task_reloader_api.auth.security;

import com.yegkim.task_reloader_api.auth.dto.AuthCookieConfig;
import com.yegkim.task_reloader_api.auth.dto.AuthCsrfConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
public class AuthCsrfProtectionFilter extends OncePerRequestFilter {

    private static final Set<String> CSRF_PROTECTED_PATHS = Set.of(
            "/api/auth/refresh",
            "/api/auth/logout"
    );

    private final AuthCookieConfig authCookieConfig;
    private final AuthCsrfConfig authCsrfConfig;
    private final SecurityErrorResponseWriter securityErrorResponseWriter;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!requiresCsrfValidation(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!hasRefreshTokenCookie(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!isOriginAllowed(request)) {
            log.warn("CSRF origin blocked method={} uri={} origin={}", request.getMethod(), request.getRequestURI(), request.getHeader("Origin"));
            securityErrorResponseWriter.write(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "CSRF_INVALID_ORIGIN",
                    "허용되지 않은 요청 출처입니다."
            );
            return;
        }

        String csrfCookieValue = resolveCookieValue(request, authCsrfConfig.cookieName());
        String csrfHeaderValue = request.getHeader(authCsrfConfig.headerName());

        if (!hasText(csrfCookieValue) || !hasText(csrfHeaderValue) || !safeEquals(csrfCookieValue, csrfHeaderValue)) {
            log.warn("CSRF token blocked method={} uri={} hasCookie={} hasHeader={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    hasText(csrfCookieValue),
                    hasText(csrfHeaderValue)
            );
            securityErrorResponseWriter.write(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "CSRF_TOKEN_INVALID",
                    "유효하지 않은 CSRF 토큰입니다."
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean requiresCsrfValidation(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return false;
        }
        return CSRF_PROTECTED_PATHS.contains(resolvePathWithoutContext(request));
    }

    private boolean hasRefreshTokenCookie(HttpServletRequest request) {
        return hasText(resolveCookieValue(request, authCookieConfig.name()));
    }

    private String resolveCookieValue(HttpServletRequest request, String cookieName) {
        Cookie cookie = WebUtils.getCookie(request, cookieName);
        return cookie != null ? cookie.getValue() : null;
    }

    private String resolvePathWithoutContext(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private boolean isOriginAllowed(HttpServletRequest request) {
        String originHeader = request.getHeader("Origin");
        if (!hasText(originHeader)) {
            return false;
        }

        String normalizedOrigin = normalizeOrigin(originHeader);
        if (normalizedOrigin == null) {
            return false;
        }

        String requestOrigin = requestOrigin(request);
        if (normalizedOrigin.equalsIgnoreCase(requestOrigin)) {
            return true;
        }

        return Arrays.stream(authCsrfConfig.allowedOrigins().split(","))
                .map(String::trim)
                .filter(this::hasText)
                .map(this::normalizeOrigin)
                .anyMatch(allowed -> allowed != null && allowed.equalsIgnoreCase(normalizedOrigin));
    }

    private String requestOrigin(HttpServletRequest request) {
        int port = request.getServerPort();
        boolean defaultPort = ("http".equalsIgnoreCase(request.getScheme()) && port == 80)
                || ("https".equalsIgnoreCase(request.getScheme()) && port == 443);
        return defaultPort
                ? request.getScheme() + "://" + request.getServerName()
                : request.getScheme() + "://" + request.getServerName() + ":" + port;
    }

    private String normalizeOrigin(String origin) {
        try {
            URI uri = URI.create(origin.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            boolean defaultPort = ("http".equalsIgnoreCase(uri.getScheme()) && uri.getPort() == 80)
                    || ("https".equalsIgnoreCase(uri.getScheme()) && uri.getPort() == 443)
                    || uri.getPort() == -1;
            return defaultPort
                    ? uri.getScheme().toLowerCase() + "://" + uri.getHost().toLowerCase()
                    : uri.getScheme().toLowerCase() + "://" + uri.getHost().toLowerCase() + ":" + uri.getPort();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean safeEquals(String left, String right) {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(leftBytes, rightBytes);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
