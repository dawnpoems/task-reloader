package com.yegkim.task_reloader_api.alert.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record TaskDueEmailAlertMailConfig(
        @Value("${task-due-email-alert.mail.from:no-reply@task-reloader.local}") String from,
        @Value("${task-due-email-alert.mail.app-url:http://localhost:5173}") String appUrl
) {
}
