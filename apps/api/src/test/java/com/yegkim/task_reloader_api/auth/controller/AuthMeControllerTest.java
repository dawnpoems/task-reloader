package com.yegkim.task_reloader_api.auth.controller;

import com.yegkim.task_reloader_api.auth.dto.AuthCookieConfig;
import com.yegkim.task_reloader_api.auth.dto.MeResponse;
import com.yegkim.task_reloader_api.auth.entity.UserRole;
import com.yegkim.task_reloader_api.auth.entity.UserStatus;
import com.yegkim.task_reloader_api.auth.exception.AuthException;
import com.yegkim.task_reloader_api.auth.security.AuthenticatedUser;
import com.yegkim.task_reloader_api.auth.security.JwtAccessDeniedHandler;
import com.yegkim.task_reloader_api.auth.security.JwtAuthenticationEntryPoint;
import com.yegkim.task_reloader_api.auth.security.JwtAuthenticationFilter;
import com.yegkim.task_reloader_api.auth.security.SecurityErrorResponseWriter;
import com.yegkim.task_reloader_api.auth.service.AuthService;
import com.yegkim.task_reloader_api.common.web.RequestIdLoggingFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
)
@ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class
})
@Import({
        AuthMeControllerTest.TestSecurityConfig.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class,
        RequestIdLoggingFilter.class
})
@DisplayName("AuthController /me 보안 단위테스트")
class AuthMeControllerTest {

    @TestConfiguration
    @EnableWebSecurity
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain securityFilterChain(
                HttpSecurity http,
                TestTokenAuthenticationFilter testTokenAuthenticationFilter,
                JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
                JwtAccessDeniedHandler jwtAccessDeniedHandler
        ) throws Exception {
            http
                    .csrf(csrf -> csrf.disable())
                    .cors(Customizer.withDefaults())
                    .httpBasic(httpBasic -> httpBasic.disable())
                    .formLogin(formLogin -> formLogin.disable())
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .exceptionHandling(exceptions -> exceptions
                            .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                            .accessDeniedHandler(jwtAccessDeniedHandler)
                    )
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(
                                    "/api/auth/signup",
                                    "/api/auth/login",
                                    "/api/auth/refresh",
                                    "/api/auth/logout"
                            ).permitAll()
                            .requestMatchers("/api/auth/me").authenticated()
                            .anyRequest().permitAll()
                    )
                    .addFilterBefore(testTokenAuthenticationFilter, AuthorizationFilter.class);

            return http.build();
        }

        @Bean
        TestTokenAuthenticationFilter testTokenAuthenticationFilter() {
            return new TestTokenAuthenticationFilter();
        }

        @Bean
        FilterRegistrationBean<TestTokenAuthenticationFilter> disableTestTokenAuthenticationFilterRegistration(
                TestTokenAuthenticationFilter filter
        ) {
            FilterRegistrationBean<TestTokenAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
            registration.setEnabled(false);
            return registration;
        }
    }

    static class TestTokenAuthenticationFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain
        ) throws ServletException, IOException {
            SecurityContextHolder.clearContext();
            String authorization = request.getHeader(AUTHORIZATION);
            if (authorization != null && authorization.startsWith("Bearer ")) {
                String token = authorization.substring("Bearer ".length()).trim();
                if ("user-token".equals(token)) {
                    AuthenticatedUser principal = new AuthenticatedUser(1L, UserRole.USER);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    principal,
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_USER"))
                            );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
            filterChain.doFilter(request, response);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AuthCookieConfig authCookieConfig;

    @MockitoBean
    private SecurityErrorResponseWriter securityErrorResponseWriter;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            HttpServletResponse response = invocation.getArgument(0);
            int status = invocation.getArgument(1);
            response.setStatus(status);
            return null;
        }).when(securityErrorResponseWriter).write(any(HttpServletResponse.class), anyInt(), anyString(), anyString());
    }

    @Test
    @DisplayName("내 정보 조회 - 인증된 사용자 성공")
    void me_success() throws Exception {
        when(authService.getMe(1L))
                .thenReturn(new MeResponse(1L, "user@example.com", UserRole.USER, UserStatus.APPROVED));

        mockMvc.perform(get("/api/auth/me")
                        .header(AUTHORIZATION, bearer("user-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.userId", is(1)))
                .andExpect(jsonPath("$.data.email", is("user@example.com")))
                .andExpect(jsonPath("$.data.role", is("USER")))
                .andExpect(jsonPath("$.data.status", is("APPROVED")));

        verify(authService).getMe(1L);
    }

    @Test
    @DisplayName("내 정보 조회 - 미인증이면 401")
    void me_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        verify(authService, never()).getMe(any());
    }

    @Test
    @DisplayName("내 정보 조회 - 사용자 없음이면 404")
    void me_userNotFound_returns404() throws Exception {
        when(authService.getMe(1L))
                .thenThrow(new AuthException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자 정보를 찾을 수 없습니다."));

        mockMvc.perform(get("/api/auth/me")
                        .header(AUTHORIZATION, bearer("user-token")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("USER_NOT_FOUND")));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
