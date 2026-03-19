package com.yegkim.task_reloader_api.task.service;

import com.yegkim.task_reloader_api.common.exception.TaskInactiveException;
import com.yegkim.task_reloader_api.common.exception.TaskNotFoundException;
import com.yegkim.task_reloader_api.common.exception.TaskRecentlyCompletedException;
import com.yegkim.task_reloader_api.common.time.TimeWindow;
import com.yegkim.task_reloader_api.task.mapper.TaskMapper;
import com.yegkim.task_reloader_api.task.dto.CreateTaskRequest;
import com.yegkim.task_reloader_api.task.dto.TaskCompletionResponse;
import com.yegkim.task_reloader_api.task.dto.UpdateTaskRequest;
import com.yegkim.task_reloader_api.task.dto.TaskResponse;
import com.yegkim.task_reloader_api.task.entity.TaskCompletion;
import com.yegkim.task_reloader_api.task.entity.Task;
import com.yegkim.task_reloader_api.task.entity.TaskStatus;
import com.yegkim.task_reloader_api.task.repository.TaskCompletionRepository;
import com.yegkim.task_reloader_api.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskService {

    // OVERDUE / TODAY / UPCOMING 모두 오래된 것부터 (next_due_at ASC)
    private static final Comparator<Task> BY_NEXT_DUE_AT_ASC =
            Comparator.comparing(Task::getNextDueAt);

    private final TaskRepository taskRepository;
    private final TaskCompletionRepository taskCompletionRepository;
    private final TaskMapper taskMapper;
    private final TaskStatusResolver taskStatusResolver;
    private final Clock clock;

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

    public TaskResponse findById(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
        return withStatus(taskMapper.toResponse(task), task, currentWindow());
    }

    public List<TaskCompletionResponse> findCompletions(Long id) {
        if (!taskRepository.existsById(id)) {
            throw new TaskNotFoundException(id);
        }

        return taskCompletionRepository.findByTaskIdOrderByCompletedAtDesc(id).stream()
                .map(this::toCompletionResponse)
                .toList();
    }

    @Transactional
    public TaskResponse create(CreateTaskRequest request) {
        Task task = taskMapper.toEntity(request);
        Task saved = taskRepository.save(task);
        return withStatus(taskMapper.toResponse(saved), saved, currentWindow());
    }

    @Transactional
    public TaskResponse update(Long id, UpdateTaskRequest request) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
        task.update(request.getName(), request.getEveryNDays(), request.getIsActive(), request.getStartDate());
        return withStatus(taskMapper.toResponse(task), task, currentWindow());
    }

    @Transactional
    public void delete(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
        taskRepository.delete(task);
    }

    private static final long COMPLETE_COOLDOWN_SECONDS = 2;

    @Transactional
    public TaskResponse complete(Long id) {
        Task task = taskRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new TaskNotFoundException(id));

        if (!task.getIsActive()) {
            throw new TaskInactiveException(id);
        }

        Instant now = clock.instant();
        OffsetDateTime previousDueAt = task.getNextDueAt();

        if (task.getLastCompletedAt() != null
                && Duration.between(task.getLastCompletedAt().toInstant(), now).toSeconds() < COMPLETE_COOLDOWN_SECONDS) {
            throw new TaskRecentlyCompletedException(id);
        }

        task.complete(now);
        taskCompletionRepository.save(TaskCompletion.builder()
                .task(task)
                .completedAt(task.getCompletedAt())
                .previousDueAt(previousDueAt)
                .nextDueAt(task.getNextDueAt())
                .build());
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
}
