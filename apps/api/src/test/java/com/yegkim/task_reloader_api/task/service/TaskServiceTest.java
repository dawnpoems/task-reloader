package com.yegkim.task_reloader_api.task.service;

import com.yegkim.task_reloader_api.common.exception.TaskNotFoundException;
import com.yegkim.task_reloader_api.task.dto.CreateTaskRequest;
import com.yegkim.task_reloader_api.task.dto.TaskResponse;
import com.yegkim.task_reloader_api.task.dto.UpdateTaskRequest;
import com.yegkim.task_reloader_api.task.entity.Task;
import com.yegkim.task_reloader_api.task.entity.TaskStatus;
import com.yegkim.task_reloader_api.task.mapper.TaskMapper;
import com.yegkim.task_reloader_api.task.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
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

    @Mock
    private TaskStatusResolver taskStatusResolver;

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
    @DisplayName("전체 작업 목록 조회 - 활성 작업만 nextDueAt 오름차순으로 반환하고 status 포함")
    void testFindAllSuccess() {
        // given
        List<Task> tasks = List.of(task);
        when(taskRepository.findAllByIsActiveTrueOrderByNextDueAtAsc()).thenReturn(tasks);
        when(taskMapper.toResponse(task)).thenReturn(taskResponse);
        when(taskStatusResolver.resolve(any(Instant.class), any())).thenReturn(TaskStatus.UPCOMING);

        // when
        List<TaskResponse> result = taskService.findAll();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getName()).isEqualTo("Test Task");
        assertThat(result.get(0).getStatus()).isEqualTo(TaskStatus.UPCOMING);
        verify(taskRepository, times(1)).findAllByIsActiveTrueOrderByNextDueAtAsc();
        verify(taskRepository, never()).findAll();
        verify(taskMapper, times(1)).toResponse(task);
    }

    @Test
    @DisplayName("status=TODAY 필터링 - TODAY인 작업만 반환하고 status 포함")
    void testFindAllWithStatusToday() {
        // given
        OffsetDateTime now = OffsetDateTime.now();
        Task todayTask = Task.builder().id(1L).name("Today Task").everyNDays(1)
                .nextDueAt(now).isActive(true).build();
        Task upcomingTask = Task.builder().id(2L).name("Upcoming Task").everyNDays(3)
                .nextDueAt(now.plusDays(3)).isActive(true).build();
        List<Task> allTasks = List.of(todayTask, upcomingTask);

        TaskResponse todayResponse = TaskResponse.builder().id(1L).name("Today Task").build();

        when(taskRepository.findAllByIsActiveTrueOrderByNextDueAtAsc()).thenReturn(allTasks);
        when(taskStatusResolver.resolve(any(Instant.class), any())).thenAnswer(invocation -> {
            Instant instant = invocation.getArgument(0);
            return instant.equals(todayTask.getNextDueAt().toInstant())
                    ? TaskStatus.TODAY
                    : TaskStatus.UPCOMING;
        });
        when(taskMapper.toResponse(todayTask)).thenReturn(todayResponse);

        // when
        List<TaskResponse> result = taskService.findAll(TaskStatus.TODAY);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Today Task");
        assertThat(result.get(0).getStatus()).isEqualTo(TaskStatus.TODAY);
        verify(taskMapper, times(1)).toResponse(todayTask);
        verify(taskMapper, never()).toResponse(upcomingTask);
    }

    @Test
    @DisplayName("status=OVERDUE 필터링 - OVERDUE인 작업만 반환하고 status 포함")
    void testFindAllWithStatusOverdue() {
        // given
        OffsetDateTime now = OffsetDateTime.now();
        Task overdueTask = Task.builder().id(1L).name("Overdue Task").everyNDays(1)
                .nextDueAt(now.minusDays(2)).isActive(true).build();
        Task todayTask = Task.builder().id(2L).name("Today Task").everyNDays(1)
                .nextDueAt(now).isActive(true).build();
        List<Task> allTasks = List.of(overdueTask, todayTask);

        TaskResponse overdueResponse = TaskResponse.builder().id(1L).name("Overdue Task").build();

        when(taskRepository.findAllByIsActiveTrueOrderByNextDueAtAsc()).thenReturn(allTasks);
        when(taskStatusResolver.resolve(any(Instant.class), any())).thenAnswer(invocation -> {
            Instant instant = invocation.getArgument(0);
            return instant.equals(overdueTask.getNextDueAt().toInstant())
                    ? TaskStatus.OVERDUE
                    : TaskStatus.TODAY;
        });
        when(taskMapper.toResponse(overdueTask)).thenReturn(overdueResponse);

        // when
        List<TaskResponse> result = taskService.findAll(TaskStatus.OVERDUE);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Overdue Task");
        assertThat(result.get(0).getStatus()).isEqualTo(TaskStatus.OVERDUE);
    }

    @Test
    @DisplayName("status 필터링 - 해당 상태의 작업이 없으면 빈 리스트 반환")
    void testFindAllWithStatusReturnsEmpty() {
        // given
        OffsetDateTime now = OffsetDateTime.now();
        Task todayTask = Task.builder().id(1L).name("Today Task").everyNDays(1)
                .nextDueAt(now).isActive(true).build();
        List<Task> allTasks = List.of(todayTask);

        when(taskRepository.findAllByIsActiveTrueOrderByNextDueAtAsc()).thenReturn(allTasks);
        when(taskStatusResolver.resolve(any(Instant.class), any())).thenReturn(TaskStatus.TODAY);

        // when
        List<TaskResponse> result = taskService.findAll(TaskStatus.UPCOMING);

        // then
        assertThat(result).isEmpty();
        verify(taskMapper, never()).toResponse(any());
    }

    @Test
    @DisplayName("status=OVERDUE 필터링 - 여러 건일 때 next_due_at ASC 정렬 유지")
    void testFindAllWithStatusSortedByNextDueAtAsc() {
        // given
        OffsetDateTime now = OffsetDateTime.now();

        Task older = Task.builder().id(1L).name("Older Overdue").everyNDays(1)
                .nextDueAt(now.minusDays(5)).isActive(true).build();
        Task newer = Task.builder().id(2L).name("Newer Overdue").everyNDays(1)
                .nextDueAt(now.minusDays(1)).isActive(true).build();

        // 일부러 역순으로 넘겨 sorted()가 없으면 순서가 깨짐을 확인
        List<Task> reversedFromDb = List.of(newer, older);

        TaskResponse olderResponse = TaskResponse.builder().id(1L).name("Older Overdue")
                .nextDueAt(now.minusDays(5)).build();
        TaskResponse newerResponse = TaskResponse.builder().id(2L).name("Newer Overdue")
                .nextDueAt(now.minusDays(1)).build();

        when(taskRepository.findAllByIsActiveTrueOrderByNextDueAtAsc()).thenReturn(reversedFromDb);
        when(taskStatusResolver.resolve(any(Instant.class), any())).thenReturn(TaskStatus.OVERDUE);
        when(taskMapper.toResponse(older)).thenReturn(olderResponse);
        when(taskMapper.toResponse(newer)).thenReturn(newerResponse);

        // when
        List<TaskResponse> result = taskService.findAll(TaskStatus.OVERDUE);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Older Overdue");  // 더 오래된 것이 먼저
        assertThat(result.get(1).getName()).isEqualTo("Newer Overdue");
    }

    @Test
    @DisplayName("전체 작업 목록 조회 - nextDueAt 오름차순 정렬 순서 검증")
    void testFindAllOrderedByNextDueAt() {
        // given
        OffsetDateTime now = OffsetDateTime.now();
        Task first = Task.builder().id(1L).name("First Task").everyNDays(1)
                .nextDueAt(now.plusDays(1)).isActive(true).build();
        Task second = Task.builder().id(2L).name("Second Task").everyNDays(1)
                .nextDueAt(now.plusDays(3)).isActive(true).build();
        Task third = Task.builder().id(3L).name("Third Task").everyNDays(1)
                .nextDueAt(now.plusDays(5)).isActive(true).build();
        List<Task> orderedTasks = List.of(first, second, third);

        when(taskRepository.findAllByIsActiveTrueOrderByNextDueAtAsc()).thenReturn(orderedTasks);
        when(taskMapper.toResponse(first)).thenReturn(
                TaskResponse.builder().id(1L).name("First Task").nextDueAt(now.plusDays(1)).build());
        when(taskMapper.toResponse(second)).thenReturn(
                TaskResponse.builder().id(2L).name("Second Task").nextDueAt(now.plusDays(3)).build());
        when(taskMapper.toResponse(third)).thenReturn(
                TaskResponse.builder().id(3L).name("Third Task").nextDueAt(now.plusDays(5)).build());
        when(taskStatusResolver.resolve(any(Instant.class), any())).thenReturn(TaskStatus.UPCOMING);

        // when
        List<TaskResponse> result = taskService.findAll();

        // then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(1).getId()).isEqualTo(2L);
        assertThat(result.get(2).getId()).isEqualTo(3L);
        assertThat(result.get(0).getNextDueAt()).isBefore(result.get(1).getNextDueAt());
        assertThat(result.get(1).getNextDueAt()).isBefore(result.get(2).getNextDueAt());
        verify(taskRepository, times(1)).findAllByIsActiveTrueOrderByNextDueAtAsc();
    }

    @Test
    @DisplayName("작업 단건 조회 - 성공 (status 포함)")
    void testFindByIdSuccess() {
        // given
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskMapper.toResponse(task)).thenReturn(taskResponse);
        when(taskStatusResolver.resolve(any(Instant.class), any())).thenReturn(TaskStatus.UPCOMING);

        // when
        TaskResponse result = taskService.findById(1L);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Test Task");
        assertThat(result.getStatus()).isEqualTo(TaskStatus.UPCOMING);
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
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining("작업을 찾을 수 없습니다")
                .hasMessageContaining("999");

        verify(taskRepository, times(1)).findById(999L);
        verify(taskMapper, never()).toResponse(any());
    }

    @Test
    @DisplayName("작업 생성 - 성공 (status 포함)")
    void testCreateSuccess() {
        // given
        CreateTaskRequest request = CreateTaskRequest.builder()
                .name("New Task")
                .everyNDays(5)
                .build();

        OffsetDateTime now = OffsetDateTime.now();
        Task newTask = Task.builder()
                .name("New Task")
                .everyNDays(5)
                .timezone("Asia/Seoul")
                .isActive(true)
                .nextDueAt(now.plusDays(5))
                .build();

        TaskResponse newTaskResponse = TaskResponse.builder()
                .id(2L)
                .name("New Task")
                .everyNDays(5)
                .timezone("Asia/Seoul")
                .nextDueAt(now.plusDays(5))
                .isActive(true)
                .build();

        when(taskMapper.toEntity(request)).thenReturn(newTask);
        when(taskRepository.save(newTask)).thenReturn(newTask);
        when(taskMapper.toResponse(newTask)).thenReturn(newTaskResponse);
        when(taskStatusResolver.resolve(any(Instant.class), any())).thenReturn(TaskStatus.UPCOMING);

        // when
        TaskResponse result = taskService.create(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("New Task");
        assertThat(result.getEveryNDays()).isEqualTo(5);
        assertThat(result.getNextDueAt()).isNotNull();
        assertThat(result.getStatus()).isEqualTo(TaskStatus.UPCOMING);
        verify(taskMapper, times(1)).toEntity(request);
        verify(taskRepository, times(1)).save(newTask);
        verify(taskMapper, times(1)).toResponse(newTask);
    }

    @Test
    @DisplayName("작업 수정 - 성공 (status 포함)")
    void testUpdateSuccess() {
        // given
        UpdateTaskRequest request = UpdateTaskRequest.builder()
                .name("Updated Task")
                .everyNDays(3)
                .isActive(false)
                .build();

        TaskResponse updatedResponse = TaskResponse.builder()
                .id(1L)
                .name("Updated Task")
                .everyNDays(3)
                .timezone("Asia/Seoul")
                .isActive(false)
                .build();

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskMapper.toResponse(task)).thenReturn(updatedResponse);
        when(taskStatusResolver.resolve(any(Instant.class), any())).thenReturn(TaskStatus.UPCOMING);

        // when
        TaskResponse result = taskService.update(1L, request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Updated Task");
        assertThat(result.getEveryNDays()).isEqualTo(3);
        assertThat(result.getIsActive()).isFalse();
        assertThat(result.getStatus()).isEqualTo(TaskStatus.UPCOMING);
        verify(taskRepository, times(1)).findById(1L);
        verify(taskMapper, times(1)).toResponse(task);
    }

    @Test
    @DisplayName("작업 수정 - 일부 필드만 수정")
    void testUpdatePartialFields() {
        // given
        UpdateTaskRequest request = UpdateTaskRequest.builder()
                .name("Partial Update")
                .build();

        TaskResponse updatedResponse = TaskResponse.builder()
                .id(1L)
                .name("Partial Update")
                .everyNDays(7)
                .timezone("Asia/Seoul")
                .isActive(true)
                .build();

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskMapper.toResponse(task)).thenReturn(updatedResponse);
        when(taskStatusResolver.resolve(any(Instant.class), any())).thenReturn(TaskStatus.UPCOMING);

        // when
        TaskResponse result = taskService.update(1L, request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Partial Update");
        assertThat(result.getEveryNDays()).isEqualTo(7);
        verify(taskRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("작업 수정 - 존재하지 않음")
    void testUpdateNotFound() {
        // given
        UpdateTaskRequest request = UpdateTaskRequest.builder()
                .name("Updated Task")
                .build();

        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> taskService.update(999L, request))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining("작업을 찾을 수 없습니다")
                .hasMessageContaining("999");

        verify(taskRepository, times(1)).findById(999L);
        verify(taskMapper, never()).toResponse(any());
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
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining("작업을 찾을 수 없습니다")
                .hasMessageContaining("999");

        verify(taskRepository, times(1)).findById(999L);
        verify(taskRepository, never()).delete(any());
    }
}

