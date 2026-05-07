package com.yegkim.task_reloader_api.auth.dto;

import com.yegkim.task_reloader_api.auth.entity.UserRole;
import com.yegkim.task_reloader_api.auth.entity.UserStatus;

import java.time.OffsetDateTime;

public record PendingUserResponse(
        Long userId,
        String email,
        UserRole role,
        UserStatus status,
        OffsetDateTime createdAt
) {
}
