package com.yegkim.task_reloader_api.task.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@AllArgsConstructor
public class TaskCompletionInsightRow {

    private Long taskId;
    private String taskName;
    private OffsetDateTime completedAt;
    private OffsetDateTime previousDueAt;
}
