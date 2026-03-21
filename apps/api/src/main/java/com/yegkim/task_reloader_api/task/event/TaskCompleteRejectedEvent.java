package com.yegkim.task_reloader_api.task.event;

public record TaskCompleteRejectedEvent(
        Long taskId,
        String reason
) {
}
