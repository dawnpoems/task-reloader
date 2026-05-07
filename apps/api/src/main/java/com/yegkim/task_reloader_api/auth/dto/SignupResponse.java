package com.yegkim.task_reloader_api.auth.dto;

import com.yegkim.task_reloader_api.auth.entity.UserStatus;

public record SignupResponse(
        Long id,
        String email,
        UserStatus status
) {
}
