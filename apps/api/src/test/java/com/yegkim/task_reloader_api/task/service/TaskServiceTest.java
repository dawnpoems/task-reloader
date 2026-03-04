package com.yegkim.task_reloader_api.task.service;

import com.yegkim.task_reloader_api.task.dto.CreateTaskRequest;
import com.yegkim.task_reloader_api.task.dto.TaskResponse;
import com.yegkim.task_reloader_api.task.entity.Task;
import com.yegkim.task_reloader_api.task.mapper.TaskMapper;
import com.yegkim.task_reloader_api.task.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TaskService 단위테스트")
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskMapper taskMapper;

    @InjectMocks
    private TaskService taskService;

    private Task task;
    private TaskResponse taskResponse;

    @BeforeEach
    void setUp() {
        OffsetDateTime now = OffsetDateTime.now();
        task = Task.builder()
                .id(1L)
                .name("Test Task")
                .everyNDays(7)
                .timezone("Asia/Seoul")
                .nextDueAt(now.plusDays(7))
                .lastCompletedAt(now.minusDays(1))
                .isActive(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        taskResponse = TaskResponse.builder()
                .id(1L)
                .name("Test Task")
                .everyNDays(7)
                .timezone("Asia/Seoul")
                .nextDueAt(now.plusDays(7))
                .lastCompletedAt(now.minusDays(1))
                .isActive(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Test
    @DisplayName("전체 작업 목록 조회 - 성공")
    void testFindAllSuccess() {
        // given
        List<Task> tasks = List.of(task);
        when(taskRepository.findAll()).thenReturn(tasks);
        when(taskMapper.toResponseList(tasks)).thenReturn(List.of(taskResponse));

        // when
        List<TaskResponse> result = taskService.findAll();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getName()).isEqualTo("Test Task");
        verify(taskRepository, times(1)).findAll();
        verify(taskMapper, times(1)).toResponseList(tasks);
    }

    @Test
    @DisplayName("작업 단건 조회 - 성공")
    void testFindByIdSuccess() {
        // given
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskMapper.toResponse(task)).thenReturn(taskResponse);

        // when
        TaskResponse result = taskService.findById(1L);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Test Task");
        verify(taskRepository, times(1)).findById(1L);
        verify(taskMapper, times(1)).toResponse(task);
    }

    @Test
    @DisplayName("작업 단건 조회 - 존재하지 않음")
    void testFindByIdNotFound() {
        // given
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> taskService.findById(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Task not found")
                .hasMessageContaining("999");

        verify(taskRepository, times(1)).findById(999L);
        verify(taskMapper, never()).toResponse(any());
    }

    @Test
    @DisplayName("작업 생성 - 성공")
    void testCreateSuccess() {
        // given
        CreateTaskRequest request = CreateTaskRequest.builder()
                .name("New Task")
                .everyNDays(5)
                .build();

        Task newTask = Task.builder()
                .name("New Task")
                .everyNDays(5)
                .timezone("Asia/Seoul")
                .isActive(true)
                .build();

        TaskResponse newTaskResponse = TaskResponse.builder()
                .id(2L)
                .name("New Task")
                .everyNDays(5)
                .timezone("Asia/Seoul")
                .nextDueAt(OffsetDateTime.now().plusDays(5))
                .isActive(true)
                .build();

        when(taskMapper.toEntity(request)).thenReturn(newTask);
        when(taskRepository.save(newTask)).thenReturn(newTask);
        when(taskMapper.toResponse(newTask)).thenReturn(newTaskResponse);

        // when
        TaskResponse result = taskService.create(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("New Task");
        assertThat(result.getEveryNDays()).isEqualTo(5);
        verify(taskMapper, times(1)).toEntity(request);
        verify(taskRepository, times(1)).save(newTask);
        verify(taskMapper, times(1)).toResponse(newTask);
    }

    @Test
    @DisplayName("작업 삭제 - 성공")
    void testDeleteSuccess() {
        // given
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        doNothing().when(taskRepository).delete(task);

        // when
        taskService.delete(1L);

        // then
        verify(taskRepository, times(1)).findById(1L);
        verify(taskRepository, times(1)).delete(task);
    }

    @Test
    @DisplayName("작업 삭제 - 존재하지 않음")
    void testDeleteNotFound() {
        // given
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> taskService.delete(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Task not found");

        verify(taskRepository, times(1)).findById(999L);
        verify(taskRepository, never()).delete(any());
    }
}

