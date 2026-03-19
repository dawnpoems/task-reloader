package com.yegkim.task_reloader_api.task.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class TaskCompletionResponse {

    private Long id;
    private OffsetDateTime completedAt;
    private OffsetDateTime previousDueAt;
    private OffsetDateTime nextDueAt;
}
