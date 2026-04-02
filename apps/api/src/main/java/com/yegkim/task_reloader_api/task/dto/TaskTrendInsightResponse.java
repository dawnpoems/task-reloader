package com.yegkim.task_reloader_api.task.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TaskTrendInsightResponse {

    private long taskId;
    private String taskName;
    private long completionCount;
    private long delayedCount;
    private double delayRatePct;
}
