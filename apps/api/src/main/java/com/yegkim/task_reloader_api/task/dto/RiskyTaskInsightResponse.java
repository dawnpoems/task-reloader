package com.yegkim.task_reloader_api.task.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Builder
public class RiskyTaskInsightResponse {

    private long taskId;
    private String taskName;
    private OffsetDateTime nextDueAt;
    private OffsetDateTime lastCompletedAt;
    private List<String> reasons;
}
