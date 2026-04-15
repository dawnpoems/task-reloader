package com.yegkim.task_reloader_api.auth.controller;

import com.yegkim.task_reloader_api.auth.dto.PendingUserResponse;
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
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.FilterType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AdminUserController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
)
@ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class
})
@Import({
        AdminUserControllerTest.TestSecurityConfig.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class,
        RequestIdLoggingFilter.class
})
@DisplayName("AdminUserController 단위테스트")
class AdminUserControllerTest {

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
                            .requestMatchers("/api/admin/**").hasRole("ADMIN")
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
                if ("admin-token".equals(token)) {
                    setAuthentication(1L, UserRole.ADMIN);
                } else if ("user-token".equals(token)) {
                    setAuthentication(2L, UserRole.USER);
                }
            }

            filterChain.doFilter(request, response);
        }

        private void setAuthentication(Long userId, UserRole role) {
            AuthenticatedUser principal = new AuthenticatedUser(userId, role);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
                    );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

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
    @DisplayName("승인 대기 사용자 목록 조회 - ADMIN 성공")
    void getPendingUsers_admin_success() throws Exception {
        OffsetDateTime createdAt = OffsetDateTime.of(2026, 4, 2, 9, 0, 0, 0, ZoneOffset.UTC);
        PendingUserResponse pending = new PendingUserResponse(
                10L,
                "pending@example.com",
                UserRole.USER,
                UserStatus.PENDING,
                createdAt
        );
        when(authService.getPendingUsers(1L)).thenReturn(List.of(pending));

        mockMvc.perform(get("/api/admin/users/pending")
                        .header(AUTHORIZATION, bearer("admin-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].userId", is(10)))
                .andExpect(jsonPath("$.data[0].email", is("pending@example.com")))
                .andExpect(jsonPath("$.data[0].status", is("PENDING")));

        verify(authService).getPendingUsers(1L);
    }

    @Test
    @DisplayName("승인/거절 사용자 목록 조회 - ADMIN 성공")
    void getNonPendingUsers_admin_success() throws Exception {
        OffsetDateTime approvedCreatedAt = OffsetDateTime.of(2026, 4, 2, 9, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime rejectedCreatedAt = OffsetDateTime.of(2026, 4, 3, 9, 0, 0, 0, ZoneOffset.UTC);
        PendingUserResponse approved = new PendingUserResponse(
                20L,
                "approved@example.com",
                UserRole.USER,
                UserStatus.APPROVED,
                approvedCreatedAt
        );
        PendingUserResponse rejected = new PendingUserResponse(
                21L,
                "rejected@example.com",
                UserRole.USER,
                UserStatus.REJECTED,
                rejectedCreatedAt
        );
        when(authService.getNonPendingUsers(1L)).thenReturn(List.of(approved, rejected));

        mockMvc.perform(get("/api/admin/users/non-pending")
                        .header(AUTHORIZATION, bearer("admin-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].status", is("APPROVED")))
                .andExpect(jsonPath("$.data[1].status", is("REJECTED")));

        verify(authService).getNonPendingUsers(1L);
    }

    @Test
    @DisplayName("승인/거절 사용자 상태 변경 - ADMIN 성공")
    void updateNonPendingUserStatus_admin_success() throws Exception {
        OffsetDateTime createdAt = OffsetDateTime.of(2026, 4, 2, 9, 0, 0, 0, ZoneOffset.UTC);
        PendingUserResponse updated = new PendingUserResponse(
                20L,
                "approved@example.com",
                UserRole.USER,
                UserStatus.REJECTED,
                createdAt
        );
        when(authService.updateNonPendingUserStatus(1L, 20L, UserStatus.REJECTED)).thenReturn(updated);

        mockMvc.perform(patch("/api/admin/users/20/status")
                        .header(AUTHORIZATION, bearer("admin-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.userId", is(20)))
                .andExpect(jsonPath("$.data.status", is("REJECTED")));

        verify(authService).updateNonPendingUserStatus(1L, 20L, UserStatus.REJECTED);
    }

    @Test
    @DisplayName("사용자 승인 - ADMIN 성공")
    void approveUser_admin_success() throws Exception {
        OffsetDateTime createdAt = OffsetDateTime.of(2026, 4, 2, 9, 0, 0, 0, ZoneOffset.UTC);
        PendingUserResponse approved = new PendingUserResponse(
                10L,
                "approved@example.com",
                UserRole.USER,
                UserStatus.APPROVED,
                createdAt
        );
        when(authService.approvePendingUser(1L, 10L)).thenReturn(approved);

        mockMvc.perform(post("/api/admin/users/10/approve")
                        .header(AUTHORIZATION, bearer("admin-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.userId", is(10)))
                .andExpect(jsonPath("$.data.email", is("approved@example.com")))
                .andExpect(jsonPath("$.data.status", is("APPROVED")));

        verify(authService).approvePendingUser(1L, 10L);
    }

    @Test
    @DisplayName("사용자 거절 - ADMIN 성공")
    void rejectUser_admin_success() throws Exception {
        OffsetDateTime createdAt = OffsetDateTime.of(2026, 4, 2, 9, 0, 0, 0, ZoneOffset.UTC);
        PendingUserResponse rejected = new PendingUserResponse(
                11L,
                "rejected@example.com",
                UserRole.USER,
                UserStatus.REJECTED,
                createdAt
        );
        when(authService.rejectPendingUser(1L, 11L)).thenReturn(rejected);

        mockMvc.perform(post("/api/admin/users/11/reject")
                        .header(AUTHORIZATION, bearer("admin-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.userId", is(11)))
                .andExpect(jsonPath("$.data.email", is("rejected@example.com")))
                .andExpect(jsonPath("$.data.status", is("REJECTED")));

        verify(authService).rejectPendingUser(1L, 11L);
    }

    @Test
    @DisplayName("사용자 승인 - 대상 사용자가 없으면 404")
    void approveUser_notFound_returns404() throws Exception {
        when(authService.approvePendingUser(1L, 999L))
                .thenThrow(new AuthException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자 정보를 찾을 수 없습니다."));

        mockMvc.perform(post("/api/admin/users/999/approve")
                        .header(AUTHORIZATION, bearer("admin-token")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("USER_NOT_FOUND")));
    }

    @Test
    @DisplayName("사용자 거절 - PENDING 상태가 아니면 409")
    void rejectUser_notPending_returns409() throws Exception {
        when(authService.rejectPendingUser(1L, 11L))
                .thenThrow(new AuthException(HttpStatus.CONFLICT, "ACCOUNT_NOT_PENDING", "승인 대기 상태 계정만 거절할 수 있습니다."));

        mockMvc.perform(post("/api/admin/users/11/reject")
                        .header(AUTHORIZATION, bearer("admin-token")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("ACCOUNT_NOT_PENDING")));
    }

    @Test
    @DisplayName("승인 대기 사용자 목록 조회 - 미인증이면 401")
    void getPendingUsers_unauthenticated_unauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/users/pending"))
                .andExpect(status().isUnauthorized());

        verify(authService, never()).getPendingUsers(any());
    }

    @Test
    @DisplayName("사용자 승인 - USER 권한이면 403")
    void approveUser_userRole_forbidden() throws Exception {
        mockMvc.perform(post("/api/admin/users/10/approve")
                        .header(AUTHORIZATION, bearer("user-token")))
                .andExpect(status().isForbidden());

        verify(authService, never()).approvePendingUser(any(), eq(10L));
    }

    @Test
    @DisplayName("승인/거절 사용자 상태 변경 - USER 권한이면 403")
    void updateNonPendingUserStatus_userRole_forbidden() throws Exception {
        mockMvc.perform(patch("/api/admin/users/20/status")
                        .header(AUTHORIZATION, bearer("user-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());

        verify(authService, never()).updateNonPendingUserStatus(any(), eq(20L), any());
    }

    @Test
    @DisplayName("사용자 거절 - USER 권한이면 403")
    void rejectUser_userRole_forbidden() throws Exception {
        mockMvc.perform(post("/api/admin/users/11/reject")
                        .header(AUTHORIZATION, bearer("user-token")))
                .andExpect(status().isForbidden());

        verify(authService, never()).rejectPendingUser(any(), eq(11L));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
