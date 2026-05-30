package com.yegkim.task_reloader_api.alert.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record TaskDueEmailAlertTaskItem(
        Long taskId,
        String name,
        OffsetDateTime nextDueAt,
        LocalDate dueDate
) {
}
