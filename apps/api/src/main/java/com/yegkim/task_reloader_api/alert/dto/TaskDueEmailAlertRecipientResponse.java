package com.yegkim.task_reloader_api.alert.dto;

import java.time.OffsetDateTime;

public record TaskDueEmailAlertRecipientResponse(
        Long id,
        String email,
        OffsetDateTime createdAt
) {
}
