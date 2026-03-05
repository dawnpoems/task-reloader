package com.yegkim.task_reloader_api.task.service;

import com.yegkim.task_reloader_api.common.exception.TaskNotFoundException;
import com.yegkim.task_reloader_api.common.time.TimeWindow;
import com.yegkim.task_reloader_api.task.mapper.TaskMapper;
import com.yegkim.task_reloader_api.task.dto.CreateTaskRequest;
import com.yegkim.task_reloader_api.task.dto.UpdateTaskRequest;
import com.yegkim.task_reloader_api.task.dto.TaskResponse;
import com.yegkim.task_reloader_api.task.entity.Task;
import com.yegkim.task_reloader_api.task.entity.TaskStatus;
import com.yegkim.task_reloader_api.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final TaskMapper taskMapper;
    private final TaskStatusResolver taskStatusResolver;

    public List<TaskResponse> findAll() {
        return taskMapper.toResponseList(
                taskRepository.findAllByIsActiveTrueOrderByNextDueAtAsc()
        );
    }

    public List<TaskResponse> findAll(TaskStatus status) {
        List<Task> tasks = taskRepository.findAllByIsActiveTrueOrderByNextDueAtAsc();
        TimeWindow window = TimeWindow.ofKst();
        return taskMapper.toResponseList(
                tasks.stream()
                        .filter(task -> taskStatusResolver.resolve(task.getNextDueAt().toInstant(), window) == status)
                        .sorted(BY_NEXT_DUE_AT_ASC)   // OVERDUE·TODAY·UPCOMING 모두 next_due_at ASC
                        .toList()
        );
    }

    public TaskResponse findById(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
        return taskMapper.toResponse(task);
    }

    @Transactional
    public TaskResponse create(CreateTaskRequest request) {
        Task task = taskMapper.toEntity(request);
        return taskMapper.toResponse(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse update(Long id, UpdateTaskRequest request) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
        task.update(request.getName(), request.getEveryNDays(), request.getIsActive());
        return taskMapper.toResponse(task);
    }

    @Transactional
    public void delete(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
        taskRepository.delete(task);
    }
}

