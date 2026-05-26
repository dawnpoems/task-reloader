package com.yegkim.task_reloader_api.alert.dto;

import java.time.LocalDate;
import java.util.List;

public record TaskDueEmailAlertSummary(
        Long userId,
        LocalDate localDate,
        String timezone,
        List<TaskDueEmailAlertTaskItem> dueTodayTasks,
        List<TaskDueEmailAlertTaskItem> overdueTasks
) {

    public TaskDueEmailAlertSummary {
        dueTodayTasks = List.copyOf(dueTodayTasks);
        overdueTasks = List.copyOf(overdueTasks);
    }

    public int dueTodayCount() {
        return dueTodayTasks.size();
    }

    public int overdueCount() {
        return overdueTasks.size();
    }

    public int totalCount() {
        return dueTodayCount() + overdueCount();
    }

    public boolean isEmpty() {
        return totalCount() == 0;
    }
}
