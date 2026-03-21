package com.yegkim.task_reloader_api.task.service;

import com.yegkim.task_reloader_api.common.exception.TaskInactiveException;
import com.yegkim.task_reloader_api.common.exception.TaskNotFoundException;
import com.yegkim.task_reloader_api.common.exception.TaskRecentlyCompletedException;
import com.yegkim.task_reloader_api.common.time.TimeWindow;
import com.yegkim.task_reloader_api.task.mapper.TaskMapper;
import com.yegkim.task_reloader_api.task.dto.CreateTaskRequest;
import com.yegkim.task_reloader_api.task.dto.DashboardSummaryResponse;
import com.yegkim.task_reloader_api.task.dto.RecentTaskCompletionResponse;
import com.yegkim.task_reloader_api.task.dto.TaskCompletionResponse;
import com.yegkim.task_reloader_api.task.dto.UpdateTaskRequest;
import com.yegkim.task_reloader_api.task.dto.TaskResponse;
import com.yegkim.task_reloader_api.task.event.TaskCompleteRejectedEvent;
import com.yegkim.task_reloader_api.task.event.TaskCompletedEvent;
import com.yegkim.task_reloader_api.task.event.TaskCreatedEvent;
import com.yegkim.task_reloader_api.task.event.TaskDeletedEvent;
import com.yegkim.task_reloader_api.task.event.TaskUpdatedEvent;
import com.yegkim.task_reloader_api.task.entity.TaskCompletion;
import com.yegkim.task_reloader_api.task.entity.Task;
import com.yegkim.task_reloader_api.task.entity.TaskStatus;
import com.yegkim.task_reloader_api.task.repository.TaskCompletionRepository;
import com.yegkim.task_reloader_api.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    // OVERDUE / TODAY / UPCOMING 모두 오래된 것부터 (next_due_at ASC)
    private static final Comparator<Task> BY_NEXT_DUE_AT_ASC =
            Comparator.comparing(Task::getNextDueAt);

    private final TaskRepository taskRepository;
    private final TaskCompletionRepository taskCompletionRepository;
    private final TaskMapper taskMapper;
    private final TaskStatusResolver taskStatusResolver;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public List<TaskResponse> findAll() {
        TimeWindow window = currentWindow();
        List<Task> tasks = taskRepository.findAllByIsActiveTrueOrderByNextDueAtAsc();
        return tasks.stream()
                .map(task -> withStatus(taskMapper.toResponse(task), task, window))
                .toList();
    }

    public List<TaskResponse> findAll(TaskStatus status) {
        List<Task> tasks = taskRepository.findAllByIsActiveTrueOrderByNextDueAtAsc();
        TimeWindow window = currentWindow();
        return tasks.stream()
                .filter(task -> taskStatusResolver.resolve(task.getNextDueAt().toInstant(), window) == status)
                .sorted(BY_NEXT_DUE_AT_ASC)   // OVERDUE·TODAY·UPCOMING 모두 next_due_at ASC
                .map(task -> withStatus(taskMapper.toResponse(task), task, window))
                .toList();
    }

    public List<TaskResponse> findDueNow() {
        List<Task> tasks = taskRepository.findAllByIsActiveTrueOrderByNextDueAtAsc();
        TimeWindow window = currentWindow();
        return tasks.stream()
                .filter(task -> {
                    TaskStatus status = taskStatusResolver.resolve(task.getNextDueAt().toInstant(), window);
                    return status == TaskStatus.OVERDUE || status == TaskStatus.TODAY;
                })
                .sorted(BY_NEXT_DUE_AT_ASC)
                .map(task -> withStatus(taskMapper.toResponse(task), task, window))
                .toList();
    }

    public TaskResponse findById(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
        return withStatus(taskMapper.toResponse(task), task, currentWindow());
    }

    public List<TaskCompletionResponse> findCompletions(Long id) {
        return findCompletions(id, null, null);
    }

    public List<TaskCompletionResponse> findCompletions(Long id, Integer year, Integer month) {
        if (!taskRepository.existsById(id)) {
            throw new TaskNotFoundException(id);
        }

        List<TaskCompletion> completions;
        if (year == null && month == null) {
            completions = taskCompletionRepository.findByTaskIdOrderByCompletedAtDesc(id);
        } else {
            if (year == null || month == null) {
                throw new IllegalArgumentException("year와 month는 함께 전달해야 합니다.");
            }
            if (month < 1 || month > 12) {
                throw new IllegalArgumentException("month는 1~12 사이여야 합니다.");
            }

            OffsetDateTime monthStart = LocalDate.of(year, month, 1).atStartOfDay(KST).toOffsetDateTime();
            OffsetDateTime nextMonthStart = monthStart.plusMonths(1);
            completions = taskCompletionRepository
                    .findByTaskIdAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtDesc(
                            id, monthStart, nextMonthStart);
        }

        return completions.stream()
                .map(this::toCompletionResponse)
                .toList();
    }

    public List<RecentTaskCompletionResponse> findRecentCompletions() {
        return taskCompletionRepository.findTop5ByOrderByCompletedAtDesc().stream()
                .map(this::toRecentCompletionResponse)
                .toList();
    }

    public DashboardSummaryResponse getDashboardSummary() {
        TimeWindow window = currentWindow();
        OffsetDateTime todayStart = window.getTodayStartUtc().atOffset(ZoneOffset.UTC);
        OffsetDateTime tomorrowStart = window.getTomorrowStartUtc().atOffset(ZoneOffset.UTC);
        OffsetDateTime sevenDaysAgo = clock.instant().minus(Duration.ofDays(7)).atOffset(ZoneOffset.UTC);

        List<Task> tasks = taskRepository.findAllByIsActiveTrueOrderByNextDueAtAsc();

        long overdue = tasks.stream()
                .filter(task -> taskStatusResolver.resolve(task.getNextDueAt().toInstant(), window) == TaskStatus.OVERDUE)
                .count();
        long today = tasks.stream()
                .filter(task -> taskStatusResolver.resolve(task.getNextDueAt().toInstant(), window) == TaskStatus.TODAY)
                .count();
        long upcoming = tasks.stream()
                .filter(task -> taskStatusResolver.resolve(task.getNextDueAt().toInstant(), window) == TaskStatus.UPCOMING)
                .count();

        return DashboardSummaryResponse.builder()
                .totalTasks(tasks.size())
                .overdueTasks(overdue)
                .todayTasks(today)
                .upcomingTasks(upcoming)
                .completedToday(taskCompletionRepository.countByCompletedAtBetween(todayStart, tomorrowStart))
                .completedLast7Days(taskCompletionRepository.countByCompletedAtGreaterThanEqual(sevenDaysAgo))
                .build();
    }

    @Transactional
    public TaskResponse create(CreateTaskRequest request) {
        Task task = taskMapper.toEntity(request);
        Task saved = taskRepository.save(task);
        eventPublisher.publishEvent(new TaskCreatedEvent(saved.getId(), saved.getEveryNDays(), saved.getIsActive()));
        return withStatus(taskMapper.toResponse(saved), saved, currentWindow());
    }

    @Transactional
    public TaskResponse update(Long id, UpdateTaskRequest request) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
        task.update(request.getName(), request.getEveryNDays(), request.getIsActive(), request.getStartDate());
        eventPublisher.publishEvent(
                new TaskUpdatedEvent(task.getId(), task.getEveryNDays(), task.getIsActive(), task.getStartDate())
        );
        return withStatus(taskMapper.toResponse(task), task, currentWindow());
    }

    @Transactional
    public void delete(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
        taskRepository.delete(task);
        eventPublisher.publishEvent(new TaskDeletedEvent(id));
    }

    private static final long COMPLETE_COOLDOWN_SECONDS = 2;

    @Transactional
    public TaskResponse complete(Long id) {
        Task task = taskRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new TaskNotFoundException(id));

        if (!task.getIsActive()) {
            eventPublisher.publishEvent(new TaskCompleteRejectedEvent(id, "inactive"));
            throw new TaskInactiveException(id);
        }

        Instant now = clock.instant();
        OffsetDateTime previousDueAt = task.getNextDueAt();

        if (task.getLastCompletedAt() != null
                && Duration.between(task.getLastCompletedAt().toInstant(), now).toSeconds() < COMPLETE_COOLDOWN_SECONDS) {
            eventPublisher.publishEvent(new TaskCompleteRejectedEvent(id, "cooldown"));
            throw new TaskRecentlyCompletedException(id);
        }

        task.complete(now);
        taskCompletionRepository.save(TaskCompletion.builder()
                .task(task)
                .completedAt(task.getCompletedAt())
                .previousDueAt(previousDueAt)
                .nextDueAt(task.getNextDueAt())
                .build());
        eventPublisher.publishEvent(
                new TaskCompletedEvent(task.getId(), task.getCompletedAt(), previousDueAt, task.getNextDueAt())
        );
        return withStatus(taskMapper.toResponse(task), task, currentWindow());
    }

    private TimeWindow currentWindow() {
        return TimeWindow.ofKst(clock);
    }

    private TaskResponse withStatus(TaskResponse response, Task task, TimeWindow window) {
        TaskStatus status = taskStatusResolver.resolve(task.getNextDueAt().toInstant(), window);
        response.setStatus(status);
        return response;
    }

    private TaskCompletionResponse toCompletionResponse(TaskCompletion completion) {
        return TaskCompletionResponse.builder()
                .id(completion.getId())
                .completedAt(completion.getCompletedAt())
                .previousDueAt(completion.getPreviousDueAt())
                .nextDueAt(completion.getNextDueAt())
                .build();
    }

    private RecentTaskCompletionResponse toRecentCompletionResponse(TaskCompletion completion) {
        return RecentTaskCompletionResponse.builder()
                .id(completion.getId())
                .taskId(completion.getTask().getId())
                .taskName(completion.getTask().getName())
                .completedAt(completion.getCompletedAt())
                .previousDueAt(completion.getPreviousDueAt())
                .nextDueAt(completion.getNextDueAt())
                .build();
    }
}
