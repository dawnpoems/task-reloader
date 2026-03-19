package com.yegkim.task_reloader_api.task.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DashboardSummaryResponse {

    private long totalTasks;
    private long overdueTasks;
    private long todayTasks;
    private long upcomingTasks;
    private long completedToday;
    private long completedLast7Days;
}
