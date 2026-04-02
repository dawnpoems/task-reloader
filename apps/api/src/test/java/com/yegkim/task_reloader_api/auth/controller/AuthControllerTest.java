package com.yegkim.task_reloader_api.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yegkim.task_reloader_api.auth.dto.AccessTokenResponse;
import com.yegkim.task_reloader_api.auth.dto.AuthCookieConfig;
import com.yegkim.task_reloader_api.auth.dto.LoginRequest;
import com.yegkim.task_reloader_api.auth.dto.LoginResponse;
import com.yegkim.task_reloader_api.auth.dto.SignupRequest;
import com.yegkim.task_reloader_api.auth.dto.SignupResponse;
import com.yegkim.task_reloader_api.auth.entity.UserRole;
import com.yegkim.task_reloader_api.auth.entity.UserStatus;
import com.yegkim.task_reloader_api.auth.exception.AuthException;
import com.yegkim.task_reloader_api.auth.jwt.JwtTokenProvider;
import com.yegkim.task_reloader_api.auth.security.SecurityErrorResponseWriter;
import com.yegkim.task_reloader_api.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.Cookie;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class
})
@DisplayName("AuthController 단위테스트")
class AuthControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            return mapper;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AuthCookieConfig authCookieConfig;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private SecurityErrorResponseWriter securityErrorResponseWriter;

    @BeforeEach
    void setUp() {
        when(authCookieConfig.name()).thenReturn("refresh_token");
        when(authCookieConfig.path()).thenReturn("/api/auth");
        when(authCookieConfig.secure()).thenReturn(false);
        when(authCookieConfig.sameSite()).thenReturn("Lax");
    }

    @Test
    @DisplayName("회원가입 성공")
    void signup_success() throws Exception {
        SignupRequest request = new SignupRequest("user@example.com", "Password1");
        SignupResponse response = new SignupResponse(1L, "user@example.com", UserStatus.PENDING);
        when(authService.signup(any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(1)))
                .andExpect(jsonPath("$.data.email", is("user@example.com")))
                .andExpect(jsonPath("$.data.status", is("PENDING")));
    }

    @Test
    @DisplayName("회원가입 유효성 실패 - 잘못된 이메일")
    void signup_validationFail_badEmail() throws Exception {
        SignupRequest invalidRequest = new SignupRequest("not-email", "Password1");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));

        verify(authService, never()).signup(any());
    }

    @Test
    @DisplayName("로그인 성공 - refresh cookie 설정")
    void login_success_setsRefreshCookie() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "Password1");
        LoginResponse response = new LoginResponse(
                "Bearer",
                "access-token",
                900L,
                1L,
                "user@example.com",
                UserRole.USER,
                UserStatus.APPROVED
        );
        when(authService.login(any())).thenReturn(new AuthService.LoginResult(response, "refresh-token", 1200L));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("refresh_token=refresh-token")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/api/auth")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=1200")))
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.data.accessToken", is("access-token")));
    }

    @Test
    @DisplayName("토큰 재발급 성공 - 요청 cookie 사용 + 새 cookie 설정")
    void refresh_success_usesCookieAndSetsNewCookie() throws Exception {
        AccessTokenResponse response = new AccessTokenResponse("Bearer", "new-access-token", 900L);
        when(authService.refresh("old-refresh-token"))
                .thenReturn(new AuthService.RefreshResult(response, "new-refresh-token", 777L));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", "old-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("refresh_token=new-refresh-token")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=777")))
                .andExpect(jsonPath("$.data.accessToken", is("new-access-token")));

        verify(authService).refresh("old-refresh-token");
    }

    @Test
    @DisplayName("토큰 재발급 실패 - 쿠키 없음")
    void refresh_fail_missingCookie() throws Exception {
        when(authService.refresh(null))
                .thenThrow(new AuthException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_MISSING", "리프레시 토큰이 없습니다."));

        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("REFRESH_TOKEN_MISSING")));

        verify(authService).refresh(null);
    }

    @Test
    @DisplayName("로그아웃 성공 - refresh cookie 제거")
    void logout_success_clearsRefreshCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("refresh_token", "logout-token")))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("refresh_token=")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

        verify(authService).logout("logout-token");
    }
}
