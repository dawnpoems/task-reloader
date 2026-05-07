package com.yegkim.task_reloader_api.auth.security;

import com.yegkim.task_reloader_api.auth.entity.UserRole;
import com.yegkim.task_reloader_api.auth.jwt.AccessTokenPayload;
import com.yegkim.task_reloader_api.auth.jwt.JwtTokenProvider;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter 단위테스트")
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private SecurityErrorResponseWriter securityErrorResponseWriter;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 다음 필터로 통과")
    void doFilter_noAuthorizationHeader_passThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        jwtAuthenticationFilter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(jwtTokenProvider);
        verifyNoInteractions(securityErrorResponseWriter);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Authorization 헤더가 Bearer 형식이 아니면 다음 필터로 통과")
    void doFilter_nonBearerHeader_passThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        jwtAuthenticationFilter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(jwtTokenProvider);
        verifyNoInteractions(securityErrorResponseWriter);
    }

    @Test
    @DisplayName("유효한 Bearer 토큰이면 SecurityContext에 인증 정보를 설정")
    void doFilter_validBearerToken_setsAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(jwtTokenProvider.parseAccessToken("access-token"))
                .thenReturn(new AccessTokenPayload(1L, UserRole.USER));

        jwtAuthenticationFilter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(securityErrorResponseWriter);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(new AuthenticatedUser(1L, UserRole.USER));
        assertThat(authentication.getAuthorities()).hasSize(1);
        assertThat(authentication.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_USER");
    }

    @Test
    @DisplayName("이미 인증 정보가 있으면 덮어쓰지 않음")
    void doFilter_whenAuthenticationExists_keepCurrentAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        Authentication existing = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(99L, UserRole.ADMIN),
                null
        );
        SecurityContextHolder.getContext().setAuthentication(existing);
        when(jwtTokenProvider.parseAccessToken("access-token"))
                .thenReturn(new AccessTokenPayload(1L, UserRole.USER));

        jwtAuthenticationFilter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isEqualTo(existing);
    }

    @Test
    @DisplayName("유효하지 않은 토큰이면 401 에러 응답을 작성하고 체인을 중단")
    void doFilter_invalidToken_writesUnauthorized() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(jwtTokenProvider.parseAccessToken("bad-token"))
                .thenThrow(new JwtException("invalid token"));

        jwtAuthenticationFilter.doFilter(request, response, chain);

        verify(securityErrorResponseWriter).write(
                response,
                401,
                "INVALID_TOKEN",
                "유효하지 않은 인증 토큰입니다."
        );
        verify(chain, never()).doFilter(any(), any());
    }
}
