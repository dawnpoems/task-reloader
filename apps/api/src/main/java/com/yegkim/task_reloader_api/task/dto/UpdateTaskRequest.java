package com.yegkim.task_reloader_api.task.dto;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateTaskRequest {

    private String name;

    @Min(value = 1, message = "everyNDays는 1 이상이어야 합니다.")
    private Integer everyNDays;

    private Boolean isActive;
}

