package com.yegkim.task_reloader_api.alert.controller;

import com.yegkim.task_reloader_api.alert.dto.TaskDueEmailAlertTestSendResponse;
import com.yegkim.task_reloader_api.alert.service.TaskDueEmailAlertTestSendService;
import com.yegkim.task_reloader_api.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Alerts", description = "알림 설정 API")
@RestController
@Profile("local")
@RequestMapping("/api/alerts/task-due-email")
@RequiredArgsConstructor
public class TaskDueEmailAlertTestSendController {

    private final TaskDueEmailAlertTestSendService testSendService;

    @Operation(summary = "로컬 개발용 작업 마감 이메일 알림 즉시 발송")
    @PostMapping("/test-send")
    public ApiResponse<TaskDueEmailAlertTestSendResponse> sendTestEmail() {
        return ApiResponse.success(testSendService.sendNow());
    }
}
