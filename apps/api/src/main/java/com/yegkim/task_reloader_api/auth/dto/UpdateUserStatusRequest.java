package com.yegkim.task_reloader_api.auth.dto;

import com.yegkim.task_reloader_api.auth.entity.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull(message = "status는 필수입니다.")
        UserStatus status
) {
}
