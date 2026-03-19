package com.yegkim.task_reloader_api.task.controller;

import com.yegkim.task_reloader_api.common.response.ApiResponse;
import com.yegkim.task_reloader_api.task.dto.DashboardSummaryResponse;
import com.yegkim.task_reloader_api.task.dto.RecentTaskCompletionResponse;
import com.yegkim.task_reloader_api.task.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Task Insights", description = "작업 대시보드 및 최근 완료 정보 API")
@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
public class TaskInsightsController {

    private final TaskService taskService;

    @Operation(summary = "대시보드 요약 조회")
    @GetMapping("/dashboard")
    public ApiResponse<DashboardSummaryResponse> getDashboard() {
        return ApiResponse.success(taskService.getDashboardSummary());
    }

    @Operation(summary = "최근 완료 작업 조회")
    @GetMapping("/recent-completions")
    public ApiResponse<List<RecentTaskCompletionResponse>> getRecentCompletions() {
        return ApiResponse.success(taskService.findRecentCompletions());
    }
}
