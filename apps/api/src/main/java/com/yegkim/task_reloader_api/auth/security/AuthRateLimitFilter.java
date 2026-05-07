package com.yegkim.task_reloader_api.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> RATE_LIMIT_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/signup",
            "/api/auth/refresh"
    );

    private final AuthRateLimitGuard authRateLimitGuard;
    private final SecurityErrorResponseWriter securityErrorResponseWriter;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = resolvePathWithoutContext(request);
        if (!requiresRateLimit(request.getMethod(), path)) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitViolation violation = authRateLimitGuard.checkIpLimit(path, request);
        if (violation == null) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("Auth rate limit blocked method={} uri={} code={} retryAfter={}",
                request.getMethod(),
                request.getRequestURI(),
                violation.code(),
                violation.retryAfterSeconds()
        );
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(violation.retryAfterSeconds()));
        securityErrorResponseWriter.write(
                response,
                429,
                violation.code(),
                violation.message()
        );
    }

    private boolean requiresRateLimit(String method, String path) {
        return HttpMethod.POST.matches(method) && RATE_LIMIT_PATHS.contains(path);
    }

    private String resolvePathWithoutContext(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }
}
