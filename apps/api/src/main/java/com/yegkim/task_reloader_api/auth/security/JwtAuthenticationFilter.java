package com.yegkim.task_reloader_api.auth.security;

import com.yegkim.task_reloader_api.auth.jwt.AccessTokenPayload;
import com.yegkim.task_reloader_api.auth.jwt.JwtTokenProvider;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityErrorResponseWriter securityErrorResponseWriter;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = resolveAccessToken(request);

        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            AccessTokenPayload payload = jwtTokenProvider.parseAccessToken(token);
            setAuthentication(request, payload);
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Invalid access token uri={} message={}", request.getRequestURI(), ex.getMessage());
            securityErrorResponseWriter.write(
                    response,
                    HttpStatus.UNAUTHORIZED.value(),
                    "INVALID_TOKEN",
                    "유효하지 않은 인증 토큰입니다."
            );
        }
    }

    private void setAuthentication(HttpServletRequest request, AccessTokenPayload payload) {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }

        AuthenticatedUser principal = new AuthenticatedUser(payload.userId(), payload.role());
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + payload.role().name())
        );

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String resolveAccessToken(HttpServletRequest request) {
        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        if (!authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
