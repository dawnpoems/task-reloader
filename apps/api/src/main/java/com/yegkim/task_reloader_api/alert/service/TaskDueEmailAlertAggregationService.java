package com.yegkim.task_reloader_api.alert.service;

import com.yegkim.task_reloader_api.alert.dto.TaskDueEmailAlertSummary;
import com.yegkim.task_reloader_api.alert.dto.TaskDueEmailAlertTaskItem;
import com.yegkim.task_reloader_api.common.time.TimeWindow;
import com.yegkim.task_reloader_api.task.entity.Task;
import com.yegkim.task_reloader_api.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskDueEmailAlertAggregationService {

    private final TaskRepository taskRepository;
    private final Clock clock;

    public TaskDueEmailAlertSummary aggregate(Long userId, String timezone) {
        ZoneId zoneId = resolveZoneId(timezone);
        TimeWindow window = TimeWindow.of(zoneId, clock);
        LocalDate localDate = LocalDate.now(clock.withZone(zoneId));
        OffsetDateTime todayStartUtc = window.getTodayStartUtc().atOffset(ZoneOffset.UTC);
        OffsetDateTime tomorrowStartUtc = window.getTomorrowStartUtc().atOffset(ZoneOffset.UTC);

        List<TaskDueEmailAlertTaskItem> overdueTasks = taskRepository
                .findAllByUserIdAndIsActiveTrueAndNextDueAtBeforeOrderByNextDueAtAsc(userId, todayStartUtc)
                .stream()
                .map(task -> toTaskItem(task, zoneId))
                .toList();

        List<TaskDueEmailAlertTaskItem> dueTodayTasks = taskRepository
                .findAllByUserIdAndIsActiveTrueAndNextDueAtGreaterThanEqualAndNextDueAtLessThanOrderByNextDueAtAsc(
                        userId,
                        todayStartUtc,
                        tomorrowStartUtc
                )
                .stream()
                .map(task -> toTaskItem(task, zoneId))
                .toList();

        return new TaskDueEmailAlertSummary(
                userId,
                localDate,
                zoneId.getId(),
                dueTodayTasks,
                overdueTasks
        );
    }

    private TaskDueEmailAlertTaskItem toTaskItem(Task task, ZoneId zoneId) {
        return new TaskDueEmailAlertTaskItem(
                task.getId(),
                task.getName(),
                task.getNextDueAt(),
                task.getNextDueAt().atZoneSameInstant(zoneId).toLocalDate()
        );
    }

    private ZoneId resolveZoneId(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            throw new IllegalArgumentException("timezone은 비워둘 수 없습니다.");
        }

        try {
            return ZoneId.of(timezone.trim());
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("지원하지 않는 timezone입니다.");
        }
    }
}
