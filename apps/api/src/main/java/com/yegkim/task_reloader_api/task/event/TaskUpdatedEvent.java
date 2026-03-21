package com.yegkim.task_reloader_api.task.event;

import java.time.LocalDate;

public record TaskUpdatedEvent(
        Long taskId,
        Integer everyNDays,
        Boolean isActive,
        LocalDate startDate
) {
}
