package com.yegkim.task_reloader_api.auth.security;

import com.yegkim.task_reloader_api.auth.dto.AuthCookieConfig;
import com.yegkim.task_reloader_api.auth.dto.AuthCsrfConfig;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthCsrfProtectionFilter 단위테스트")
class AuthCsrfProtectionFilterTest {

    @Mock
    private AuthCookieConfig authCookieConfig;

    @Mock
    private AuthCsrfConfig authCsrfConfig;

    @Mock
    private SecurityErrorResponseWriter securityErrorResponseWriter;

    @InjectMocks
    private AuthCsrfProtectionFilter authCsrfProtectionFilter;

    @Test
    @DisplayName("대상 엔드포인트가 아니면 그대로 통과")
    void doFilter_nonProtectedEndpoint_passThrough() throws Exception {
        configureDefaults();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tasks");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        authCsrfProtectionFilter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(securityErrorResponseWriter, never()).write(any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("refresh cookie가 없으면 CSRF 검증 없이 통과")
    void doFilter_noRefreshCookie_passThrough() throws Exception {
        configureDefaults();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh");
        request.addHeader("Origin", "http://localhost:5173");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        authCsrfProtectionFilter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(securityErrorResponseWriter, never()).write(any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("refresh cookie가 있고 Origin이 없으면 차단")
    void doFilter_missingOrigin_blocked() throws Exception {
        configureDefaults();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh");
        request.setCookies(new jakarta.servlet.http.Cookie("refresh_token", "r1"));
        request.setCookies(
                new jakarta.servlet.http.Cookie("refresh_token", "r1"),
                new jakarta.servlet.http.Cookie("csrf_token", "c1")
        );
        request.addHeader("X-CSRF-Token", "c1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        authCsrfProtectionFilter.doFilter(request, response, chain);

        verify(securityErrorResponseWriter).write(
                eq(response),
                eq(403),
                eq("CSRF_INVALID_ORIGIN"),
                eq("허용되지 않은 요청 출처입니다.")
        );
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("허용되지 않은 Origin이면 차단")
    void doFilter_invalidOrigin_blocked() throws Exception {
        configureDefaults();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/logout");
        request.setCookies(
                new jakarta.servlet.http.Cookie("refresh_token", "r1"),
                new jakarta.servlet.http.Cookie("csrf_token", "c1")
        );
        request.addHeader("Origin", "https://attacker.example");
        request.addHeader("X-CSRF-Token", "c1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        authCsrfProtectionFilter.doFilter(request, response, chain);

        verify(securityErrorResponseWriter).write(
                eq(response),
                eq(403),
                eq("CSRF_INVALID_ORIGIN"),
                eq("허용되지 않은 요청 출처입니다.")
        );
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("CSRF 헤더/쿠키 불일치면 차단")
    void doFilter_csrfTokenMismatch_blocked() throws Exception {
        configureDefaults();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh");
        request.setCookies(
                new jakarta.servlet.http.Cookie("refresh_token", "r1"),
                new jakarta.servlet.http.Cookie("csrf_token", "cookie-token")
        );
        request.addHeader("Origin", "http://localhost:5173");
        request.addHeader("X-CSRF-Token", "header-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        authCsrfProtectionFilter.doFilter(request, response, chain);

        verify(securityErrorResponseWriter).write(
                eq(response),
                eq(403),
                eq("CSRF_TOKEN_INVALID"),
                eq("유효하지 않은 CSRF 토큰입니다.")
        );
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Origin 허용 + CSRF 일치면 통과")
    void doFilter_validOriginAndCsrf_passThrough() throws Exception {
        configureDefaults();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh");
        request.setCookies(
                new jakarta.servlet.http.Cookie("refresh_token", "r1"),
                new jakarta.servlet.http.Cookie("csrf_token", "csrf-ok")
        );
        request.addHeader("Origin", "http://localhost:5173");
        request.addHeader("X-CSRF-Token", "csrf-ok");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        authCsrfProtectionFilter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(securityErrorResponseWriter, never()).write(any(), anyInt(), any(), any());
    }

    private void configureDefaults() {
        lenient().when(authCookieConfig.name()).thenReturn("refresh_token");
        lenient().when(authCsrfConfig.cookieName()).thenReturn("csrf_token");
        lenient().when(authCsrfConfig.headerName()).thenReturn("X-CSRF-Token");
        lenient().when(authCsrfConfig.allowedOrigins()).thenReturn("http://localhost:5173,http://127.0.0.1:5173");
    }
}
