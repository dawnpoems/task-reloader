package com.yegkim.task_reloader_api.task.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class RecentTaskCompletionResponse {

    private Long id;
    private Long taskId;
    private String taskName;
    private OffsetDateTime completedAt;
    private OffsetDateTime previousDueAt;
    private OffsetDateTime nextDueAt;
}
