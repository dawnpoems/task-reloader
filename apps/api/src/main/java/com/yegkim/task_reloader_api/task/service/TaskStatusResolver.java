package com.yegkim.task_reloader_api.task.service;

import com.yegkim.task_reloader_api.common.time.TimeWindow;
import com.yegkim.task_reloader_api.task.entity.TaskStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class TaskStatusResolver {

    /**
     * nextDueAt(UTC Instant)을 TimeWindow의 KST 경계값과 비교해 상태를 판정한다.
     *
     * - nextDueAt < todayStart  → OVERDUE
     * - nextDueAt < tomorrowStart → TODAY
     * - 그 외                   → UPCOMING
     */
    public TaskStatus resolve(Instant nextDueAt, TimeWindow window) {
        if (nextDueAt.isBefore(window.getTodayStartUtc())) {
            return TaskStatus.OVERDUE;
        }
        if (nextDueAt.isBefore(window.getTomorrowStartUtc())) {
            return TaskStatus.TODAY;
        }
        return TaskStatus.UPCOMING;
    }
}

