package com.yegkim.task_reloader_api.task.service;

import com.yegkim.task_reloader_api.auth.security.AuthenticatedUserProvider;
import com.yegkim.task_reloader_api.common.exception.TaskNotFoundException;
import com.yegkim.task_reloader_api.task.dto.CreateTaskRequest;
import com.yegkim.task_reloader_api.task.dto.TaskResponse;
import com.yegkim.task_reloader_api.task.entity.Task;
import com.yegkim.task_reloader_api.task.entity.TaskStatus;
import com.yegkim.task_reloader_api.task.mapper.TaskMapper;
import com.yegkim.task_reloader_api.task.repository.TaskCompletionRepository;
import com.yegkim.task_reloader_api.task.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TaskService 사용자 스코프 단위테스트")
class TaskServiceUserScopeTest {

    private static final long USER_ID = 42L;

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private TaskCompletionRepository taskCompletionRepository;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private TaskStatusResolver taskStatusResolver;
    @Mock
    private Clock clock;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private AuthenticatedUserProvider authenticatedUserProvider;

    @InjectMocks
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        lenient().when(authenticatedUserProvider.currentUserId()).thenReturn(USER_ID);
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-04-02T00:00:00Z"));
    }

    @Test
    @DisplayName("단건 조회는 사용자 소유 범위로 조회한다")
    void findById_scopedByUser() {
        Task task = Task.builder()
                .id(10L)
                .userId(USER_ID)
                .name("Scoped Task")
                .everyNDays(3)
                .timezone("Asia/Seoul")
                .nextDueAt(OffsetDateTime.of(2026, 4, 3, 0, 0, 0, 0, ZoneOffset.UTC))
                .isActive(true)
                .build();
        TaskResponse response = TaskResponse.builder().id(10L).name("Scoped Task").build();

        when(taskRepository.findByIdAndUserId(10L, USER_ID)).thenReturn(Optional.of(task));
        when(taskMapper.toResponse(task)).thenReturn(response);
        when(taskStatusResolver.resolve(any(), any())).thenReturn(TaskStatus.UPCOMING);

        TaskResponse actual = taskService.findById(10L);

        assertThat(actual.getId()).isEqualTo(10L);
        verify(taskRepository).findByIdAndUserId(10L, USER_ID);
    }

    @Test
    @DisplayName("다른 사용자 작업은 찾을 수 없음으로 처리한다")
    void findById_otherUserTask_throwsNotFound() {
        when(taskRepository.findByIdAndUserId(10L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.findById(10L))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    @DisplayName("생성 시 현재 사용자 ID를 owner로 설정한다")
    void create_assignsCurrentUserAsOwner() {
        CreateTaskRequest request = CreateTaskRequest.builder()
                .name("New Task")
                .everyNDays(7)
                .build();

        Task entity = Task.builder()
                .name("New Task")
                .everyNDays(7)
                .timezone("Asia/Seoul")
                .nextDueAt(OffsetDateTime.of(2026, 4, 9, 0, 0, 0, 0, ZoneOffset.UTC))
                .isActive(true)
                .build();
        Task saved = Task.builder()
                .id(100L)
                .userId(USER_ID)
                .name("New Task")
                .everyNDays(7)
                .timezone("Asia/Seoul")
                .nextDueAt(OffsetDateTime.of(2026, 4, 9, 0, 0, 0, 0, ZoneOffset.UTC))
                .isActive(true)
                .build();
        TaskResponse response = TaskResponse.builder().id(100L).name("New Task").build();

        when(taskMapper.toEntity(request)).thenReturn(entity);
        when(taskRepository.save(entity)).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            assertThat(task.getUserId()).isEqualTo(USER_ID);
            return saved;
        });
        when(taskMapper.toResponse(saved)).thenReturn(response);
        when(taskStatusResolver.resolve(any(), any())).thenReturn(TaskStatus.UPCOMING);

        TaskResponse actual = taskService.create(request);

        assertThat(actual.getId()).isEqualTo(100L);
        verify(taskRepository).save(entity);
        verify(authenticatedUserProvider).currentUserId();
    }

    @Test
    @DisplayName("완료 처리도 사용자 소유 범위로 잠금 조회한다")
    void complete_scopedByUserForUpdate() {
        Task task = Task.builder()
                .id(55L)
                .userId(USER_ID)
                .name("Complete Me")
                .everyNDays(1)
                .timezone("Asia/Seoul")
                .nextDueAt(OffsetDateTime.of(2026, 4, 2, 0, 0, 0, 0, ZoneOffset.UTC))
                .isActive(true)
                .build();
        TaskResponse response = TaskResponse.builder().id(55L).name("Complete Me").build();

        when(taskRepository.findByIdAndUserIdForUpdate(55L, USER_ID)).thenReturn(Optional.of(task));
        when(taskMapper.toResponse(task)).thenReturn(response);
        when(taskStatusResolver.resolve(any(), any())).thenReturn(TaskStatus.UPCOMING);

        taskService.complete(55L);

        verify(taskRepository).findByIdAndUserIdForUpdate(55L, USER_ID);
        verify(taskCompletionRepository).save(any());
    }
}
