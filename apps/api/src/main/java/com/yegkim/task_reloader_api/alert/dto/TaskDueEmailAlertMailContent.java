package com.yegkim.task_reloader_api.alert.dto;

public record TaskDueEmailAlertMailContent(
        String subject,
        String htmlBody,
        String textBody
) {
}
