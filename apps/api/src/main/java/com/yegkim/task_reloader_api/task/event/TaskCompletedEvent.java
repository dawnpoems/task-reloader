package com.yegkim.task_reloader_api.task.event;

import java.time.OffsetDateTime;

public record TaskCompletedEvent(
        Long taskId,
        OffsetDateTime completedAt,
        OffsetDateTime previousDueAt,
        OffsetDateTime nextDueAt
) {
}
