package com.yegkim.task_reloader_api.task.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Builder
public class InsightsOverviewResponse {

    private int periodDays;
    private OffsetDateTime periodStart;
    private OffsetDateTime periodEnd;
    private String timezone;
    private long activeTaskCount;
    private long completedTaskCount;
    private long completionCount;
    private long delayedCompletionCount;
    private double completionRatePct;
    private double delayRatePct;
    private double averageDelayDays;
    private long riskyTaskCount;
    private List<RiskyTaskInsightResponse> riskyTasks;
    private List<TaskTrendInsightResponse> topCompletionTrends;
    private List<TaskTrendInsightResponse> topDelayedTrends;
    private List<TaskTrendInsightResponse> topDelayRateTrends;
    private List<TaskTrendInsightResponse> taskTrends;
}
