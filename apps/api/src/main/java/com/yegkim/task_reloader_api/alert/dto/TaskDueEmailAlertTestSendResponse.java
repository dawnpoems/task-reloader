package com.yegkim.task_reloader_api.alert.dto;

import java.time.LocalDate;

public record TaskDueEmailAlertTestSendResponse(
        int sentCount,
        int recipientCount,
        int dueTodayCount,
        int overdueCount,
        int totalCount,
        LocalDate localDate,
        String timezone,
        String skippedReason
) {
}
