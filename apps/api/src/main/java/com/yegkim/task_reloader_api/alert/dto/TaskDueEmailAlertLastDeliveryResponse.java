package com.yegkim.task_reloader_api.alert.dto;

import com.yegkim.task_reloader_api.alert.entity.TaskDueEmailAlertDeliveryStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record TaskDueEmailAlertLastDeliveryResponse(
        TaskDueEmailAlertDeliveryStatus status,
        LocalDate localDate,
        int attemptCount,
        int recipientCount,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
