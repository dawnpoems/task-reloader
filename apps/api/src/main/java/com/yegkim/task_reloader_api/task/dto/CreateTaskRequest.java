package com.yegkim.task_reloader_api.task.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTaskRequest {

    @NotBlank(message = "name은 필수입니다.")
    private String name;

    @NotNull(message = "everyNDays는 필수입니다.")
    @Min(value = 1, message = "everyNDays는 1 이상이어야 합니다.")
    private Integer everyNDays;

    private LocalDate startDate;
}
