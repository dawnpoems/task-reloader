package com.yegkim.task_reloader_api.auth.dto;

import com.yegkim.task_reloader_api.auth.entity.UserRole;
import com.yegkim.task_reloader_api.auth.entity.UserStatus;

public record LoginResponse(
        String tokenType,
        String accessToken,
        long expiresInSeconds,
        Long userId,
        String email,
        UserRole role,
        UserStatus status
) {
}
