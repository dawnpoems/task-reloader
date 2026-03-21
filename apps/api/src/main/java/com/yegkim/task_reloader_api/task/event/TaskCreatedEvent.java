package com.yegkim.task_reloader_api.task.event;

public record TaskCreatedEvent(
        Long taskId,
        Integer everyNDays,
        Boolean isActive
) {
}
