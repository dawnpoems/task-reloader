package com.yegkim.task_reloader_api.task.dto;

import com.yegkim.task_reloader_api.task.entity.TaskStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class TaskResponse {

    private Long id;
    private String name;
    private Integer everyNDays;
    private String timezone;
    @Setter
    private TaskStatus status;
    private OffsetDateTime nextDueAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime lastCompletedAt;
    private Boolean isActive;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}

