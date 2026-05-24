package com.yegkim.task_reloader_api.alert.controller;

import com.yegkim.task_reloader_api.alert.dto.AddTaskDueEmailAlertRecipientRequest;
import com.yegkim.task_reloader_api.alert.dto.TaskDueEmailAlertRecipientResponse;
import com.yegkim.task_reloader_api.alert.dto.TaskDueEmailAlertSettingsResponse;
import com.yegkim.task_reloader_api.alert.dto.UpdateTaskDueEmailAlertSettingsRequest;
import com.yegkim.task_reloader_api.alert.service.TaskDueEmailAlertService;
import com.yegkim.task_reloader_api.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Alerts", description = "알림 설정 API")
@RestController
@RequestMapping("/api/alerts/task-due-email")
@RequiredArgsConstructor
public class TaskDueEmailAlertController {

    private final TaskDueEmailAlertService taskDueEmailAlertService;

    @Operation(summary = "작업 마감 이메일 알림 설정 조회")
    @GetMapping("/settings")
    public ApiResponse<TaskDueEmailAlertSettingsResponse> getSettings() {
        return ApiResponse.success(taskDueEmailAlertService.getSettings());
    }

    @Operation(summary = "작업 마감 이메일 알림 설정 수정")
    @PatchMapping("/settings")
    public ApiResponse<TaskDueEmailAlertSettingsResponse> updateSettings(
            @Valid @RequestBody UpdateTaskDueEmailAlertSettingsRequest request
    ) {
        return ApiResponse.success(taskDueEmailAlertService.updateSettings(request));
    }

    @Operation(summary = "작업 마감 이메일 알림 수신자 목록 조회")
    @GetMapping("/recipients")
    public ApiResponse<List<TaskDueEmailAlertRecipientResponse>> findRecipients() {
        return ApiResponse.success(taskDueEmailAlertService.findRecipients());
    }

    @Operation(summary = "작업 마감 이메일 알림 수신자 추가")
    @PostMapping("/recipients")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TaskDueEmailAlertRecipientResponse> addRecipient(
            @Valid @RequestBody AddTaskDueEmailAlertRecipientRequest request
    ) {
        return ApiResponse.success(taskDueEmailAlertService.addRecipient(request));
    }

    @Operation(summary = "작업 마감 이메일 알림 수신자 삭제")
    @DeleteMapping("/recipients/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteRecipient(@PathVariable Long id) {
        taskDueEmailAlertService.deleteRecipient(id);
        return ApiResponse.success(null);
    }
}
