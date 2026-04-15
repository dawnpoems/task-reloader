package com.yegkim.task_reloader_api.auth.controller;

import com.yegkim.task_reloader_api.auth.dto.PendingUserResponse;
import com.yegkim.task_reloader_api.auth.security.AuthenticatedUser;
import com.yegkim.task_reloader_api.auth.service.AuthService;
import com.yegkim.task_reloader_api.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin Users", description = "관리자 사용자 승인 API")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AuthService authService;

    @Operation(summary = "승인/거절 사용자 목록 조회 (PENDING 제외)")
    @GetMapping("/non-pending")
    public ApiResponse<List<PendingUserResponse>> getNonPendingUsers(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(authService.getNonPendingUsers(principal.userId()));
    }

    @Operation(summary = "승인 대기(PENDING) 사용자 목록 조회")
    @GetMapping("/pending")
    public ApiResponse<List<PendingUserResponse>> getPendingUsers(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(authService.getPendingUsers(principal.userId()));
    }

    @Operation(summary = "사용자 승인")
    @PostMapping("/{userId}/approve")
    public ApiResponse<PendingUserResponse> approveUser(
            @PathVariable Long userId,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(authService.approvePendingUser(principal.userId(), userId));
    }

    @Operation(summary = "사용자 거절")
    @PostMapping("/{userId}/reject")
    public ApiResponse<PendingUserResponse> rejectUser(
            @PathVariable Long userId,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(authService.rejectPendingUser(principal.userId(), userId));
    }
}
