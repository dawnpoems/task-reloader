package com.yegkim.task_reloader_api.task.controller;

import com.yegkim.task_reloader_api.common.response.ApiResponse;
import com.yegkim.task_reloader_api.task.dto.DashboardSummaryResponse;
import com.yegkim.task_reloader_api.task.dto.InsightsOverviewResponse;
import com.yegkim.task_reloader_api.task.dto.RecentTaskCompletionResponse;
import com.yegkim.task_reloader_api.task.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @Operation(summary = "인사이트 요약 조회 (완료율/지연률/평균 지연날짜/작업별 추세)")
    @GetMapping("/overview")
    public ApiResponse<InsightsOverviewResponse> getOverview(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "5") int top
    ) {
        return ApiResponse.success(taskService.getInsightsOverview(days, top));
    }

    @Operation(summary = "최근 완료 작업 조회")
    @GetMapping("/recent-completions")
    public ApiResponse<List<RecentTaskCompletionResponse>> getRecentCompletions() {
        return ApiResponse.success(taskService.findRecentCompletions());
    }
}
