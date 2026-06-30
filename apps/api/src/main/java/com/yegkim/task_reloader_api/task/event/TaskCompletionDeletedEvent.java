package com.yegkim.task_reloader_api.task.event;

import java.time.OffsetDateTime;

public record TaskCompletionDeletedEvent(
        Long taskId,
        Long completionId,
        OffsetDateTime deletedCompletedAt,
        OffsetDateTime restoredLastCompletedAt,
        OffsetDateTime restoredNextDueAt
) {
}
