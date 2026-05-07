package com.yegkim.task_reloader_api.auth.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthRateLimitFilter 단위테스트")
class AuthRateLimitFilterTest {

    @Mock
    private AuthRateLimitGuard authRateLimitGuard;

    @Mock
    private SecurityErrorResponseWriter securityErrorResponseWriter;

    @InjectMocks
    private AuthRateLimitFilter authRateLimitFilter;

    @Test
    @DisplayName("대상 경로가 아니면 통과")
    void doFilter_nonTargetPath_passThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/tasks");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        authRateLimitFilter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(authRateLimitGuard, never()).checkIpLimit(any(), any());
        verify(securityErrorResponseWriter, never()).write(any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("POST가 아니면 통과")
    void doFilter_nonPostMethod_passThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        authRateLimitFilter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(authRateLimitGuard, never()).checkIpLimit(any(), any());
        verify(securityErrorResponseWriter, never()).write(any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("제한 초과가 아니면 통과")
    void doFilter_allowed_passThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(authRateLimitGuard.checkIpLimit("/api/auth/login", request)).thenReturn(null);

        authRateLimitFilter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(securityErrorResponseWriter, never()).write(any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("제한 초과면 429 + Retry-After 헤더와 에러 응답 작성")
    void doFilter_blocked_writes429AndRetryAfter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        RateLimitViolation violation = new RateLimitViolation(
                "REFRESH_RATE_LIMITED",
                "세션 갱신 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.",
                7L
        );
        when(authRateLimitGuard.checkIpLimit("/api/auth/refresh", request)).thenReturn(violation);

        authRateLimitFilter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getHeader("Retry-After")).isEqualTo("7");
        verify(securityErrorResponseWriter).write(
                eq(response),
                eq(429),
                eq("REFRESH_RATE_LIMITED"),
                eq("세션 갱신 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.")
        );
    }
}
