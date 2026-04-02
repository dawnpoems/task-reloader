package com.yegkim.task_reloader_api.auth.controller;

import com.yegkim.task_reloader_api.auth.dto.*;
import com.yegkim.task_reloader_api.auth.security.AuthenticatedUser;
import com.yegkim.task_reloader_api.auth.service.AuthService;
import com.yegkim.task_reloader_api.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.WebUtils;

import java.time.Duration;

@Tag(name = "Auth", description = "회원가입/로그인/토큰 재발급 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthCookieConfig authCookieConfig;

    @Operation(summary = "회원가입")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.success(authService.signup(request));
    }

    @Operation(summary = "로그인")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        AuthService.LoginResult result = authService.login(request);
        attachRefreshCookie(response, result.refreshToken(), result.refreshTtlSeconds());
        return ApiResponse.success(result.response());
    }

    @Operation(summary = "Access Token 재발급 (Refresh Token 회전)")
    @PostMapping("/refresh")
    public ApiResponse<AccessTokenResponse> refresh(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String refreshToken = extractRefreshTokenFromCookie(request);
        AuthService.RefreshResult result = authService.refresh(refreshToken);
        attachRefreshCookie(response, result.refreshToken(), result.refreshTtlSeconds());
        return ApiResponse.success(result.response());
    }

    @Operation(summary = "로그아웃")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        authService.logout(extractRefreshTokenFromCookie(request));
        clearRefreshCookie(response);
        return ApiResponse.success(null);
    }

    @Operation(summary = "내 정보 조회")
    @GetMapping("/me")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(authService.getMe(principal.userId()));
    }

    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        var cookie = WebUtils.getCookie(request, authCookieConfig.name());
        return cookie != null ? cookie.getValue() : null;
    }

    private void attachRefreshCookie(HttpServletResponse response, String refreshToken, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(authCookieConfig.name(), refreshToken)
                .httpOnly(true)
                .secure(authCookieConfig.secure())
                .sameSite(authCookieConfig.sameSite())
                .path(authCookieConfig.path())
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(authCookieConfig.name(), "")
                .httpOnly(true)
                .secure(authCookieConfig.secure())
                .sameSite(authCookieConfig.sameSite())
                .path(authCookieConfig.path())
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
