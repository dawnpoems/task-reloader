package com.yegkim.task_reloader_api.task.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class TaskResponse {

    private Long id;
    private String name;
    private Integer everyNDays;
    private String timezone;
    private OffsetDateTime nextDueAt;
    private OffsetDateTime lastCompletedAt;
    private Boolean isActive;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}

