package com.yegkim.task_reloader_api.alert.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record UpdateTaskDueEmailAlertSettingsRequest(
        Boolean enabled,
        LocalTime sendTime,

        @Size(max = 100, message = "timezone은 100자 이하여야 합니다.")
        String timezone
) {
}
