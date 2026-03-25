package com.yegkim.task_reloader_api.task.service;

import com.yegkim.task_reloader_api.common.exception.TaskInactiveException;
import com.yegkim.task_reloader_api.common.exception.TaskNotFoundException;
import com.yegkim.task_reloader_api.common.exception.TaskRecentlyCompletedException;
import com.yegkim.task_reloader_api.task.dto.CreateTaskRequest;
import com.yegkim.task_reloader_api.task.dto.DashboardSummaryResponse;
import com.yegkim.task_reloader_api.task.dto.InsightsOverviewResponse;
import com.yegkim.task_reloader_api.task.dto.RecentTaskCompletionResponse;
import com.yegkim.task_reloader_api.task.dto.RiskyTaskInsightResponse;
import com.yegkim.task_reloader_api.task.dto.TaskCompletionResponse;
import com.yegkim.task_reloader_api.task.dto.TaskResponse;
import com.yegkim.task_reloader_api.task.dto.TaskTrendInsightResponse;
import com.yegkim.task_reloader_api.task.dto.UpdateTaskRequest;
import com.yegkim.task_reloader_api.task.entity.TaskCompletion;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TaskService 단위테스트")
class TaskServiceTest {

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

    @InjectMocks
    private TaskService taskService;

    private Task task;
    private TaskResponse taskResponse;

    @BeforeEach
    void setUp() {
        OffsetDateTime now = OffsetDateTime.now();
        lenient().when(clock.instant()).thenReturn(now.toInstant());

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
    @DisplayName("DUE_NOW 조회 - OVERDUE와 TODAY를 함께 반환하고 status 포함")
    void testFindDueNow() {
        OffsetDateTime now = OffsetDateTime.now();
        Task overdueTask = Task.builder().id(1L).name("Overdue Task").everyNDays(1)
                .nextDueAt(now.minusDays(2)).isActive(true).build();
        Task todayTask = Task.builder().id(2L).name("Today Task").everyNDays(1)
                .nextDueAt(now).isActive(true).build();
        Task upcomingTask = Task.builder().id(3L).name("Upcoming Task").everyNDays(1)
                .nextDueAt(now.plusDays(2)).isActive(true).build();

        TaskResponse overdueResponse = TaskResponse.builder().id(1L).name("Overdue Task").build();
        TaskResponse todayResponse = TaskResponse.builder().id(2L).name("Today Task").build();

        when(taskRepository.findAllByIsActiveTrueOrderByNextDueAtAsc()).thenReturn(List.of(overdueTask, todayTask, upcomingTask));
        when(taskStatusResolver.resolve(eq(overdueTask.getNextDueAt().toInstant()), any())).thenReturn(TaskStatus.OVERDUE);
        when(taskStatusResolver.resolve(eq(todayTask.getNextDueAt().toInstant()), any())).thenReturn(TaskStatus.TODAY);
        when(taskStatusResolver.resolve(eq(upcomingTask.getNextDueAt().toInstant()), any())).thenReturn(TaskStatus.UPCOMING);
        when(taskMapper.toResponse(overdueTask)).thenReturn(overdueResponse);
        when(taskMapper.toResponse(todayTask)).thenReturn(todayResponse);

        List<TaskResponse> result = taskService.findDueNow();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getStatus()).isEqualTo(TaskStatus.OVERDUE);
        assertThat(result.get(1).getStatus()).isEqualTo(TaskStatus.TODAY);
        verify(taskMapper, never()).toResponse(upcomingTask);
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
    @DisplayName("작업 완료 이력 조회 - 최신 완료 시각 순으로 반환")
    void testFindCompletionsSuccess() {
        OffsetDateTime now = OffsetDateTime.now();
        TaskCompletion newer = TaskCompletion.builder()
                .id(2L)
                .task(task)
                .completedAt(now.minusDays(1))
                .previousDueAt(now.minusDays(2))
                .nextDueAt(now.plusDays(6))
                .build();
        TaskCompletion older = TaskCompletion.builder()
                .id(1L)
                .task(task)
                .completedAt(now.minusDays(3))
                .previousDueAt(now.minusDays(4))
                .nextDueAt(now.plusDays(4))
                .build();

        when(taskRepository.existsById(1L)).thenReturn(true);
        when(taskCompletionRepository.findByTaskIdOrderByCompletedAtDesc(1L)).thenReturn(List.of(newer, older));

        List<TaskCompletionResponse> result = taskService.findCompletions(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(2L);
        assertThat(result.get(0).getCompletedAt()).isEqualTo(newer.getCompletedAt());
        assertThat(result.get(1).getId()).isEqualTo(1L);
        verify(taskRepository, times(1)).existsById(1L);
        verify(taskCompletionRepository, times(1)).findByTaskIdOrderByCompletedAtDesc(1L);
    }

    @Test
    @DisplayName("작업 완료 이력 조회 - year/month 전달 시 해당 월만 조회")
    void testFindCompletionsByMonthSuccess() {
        OffsetDateTime now = OffsetDateTime.now();
        TaskCompletion completion = TaskCompletion.builder()
                .id(10L)
                .task(task)
                .completedAt(now.minusDays(1))
                .previousDueAt(now.minusDays(2))
                .nextDueAt(now.plusDays(5))
                .build();

        OffsetDateTime monthStart = LocalDate.of(2026, 3, 1).atStartOfDay(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
        OffsetDateTime nextMonthStart = monthStart.plusMonths(1);

        when(taskRepository.existsById(1L)).thenReturn(true);
        when(taskCompletionRepository
                .findByTaskIdAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtDesc(
                        1L, monthStart, nextMonthStart))
                .thenReturn(List.of(completion));

        List<TaskCompletionResponse> result = taskService.findCompletions(1L, 2026, 3);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(10L);
        verify(taskCompletionRepository, times(1))
                .findByTaskIdAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtDesc(
                        1L, monthStart, nextMonthStart);
    }

    @Test
    @DisplayName("작업 완료 이력 조회 - year/month 중 하나만 전달하면 예외")
    void testFindCompletionsByMonthRequiresBothYearAndMonth() {
        when(taskRepository.existsById(1L)).thenReturn(true);

        assertThatThrownBy(() -> taskService.findCompletions(1L, 2026, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("함께 전달");
    }

    @Test
    @DisplayName("작업 완료 이력 조회 - month 범위가 잘못되면 예외")
    void testFindCompletionsByMonthOutOfRange() {
        when(taskRepository.existsById(1L)).thenReturn(true);

        assertThatThrownBy(() -> taskService.findCompletions(1L, 2026, 13))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1~12");
    }

    @Test
    @DisplayName("작업 완료 이력 조회 - Task가 없으면 예외")
    void testFindCompletionsNotFound() {
        when(taskRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> taskService.findCompletions(999L))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining("999");

        verify(taskRepository, times(1)).existsById(999L);
        verify(taskCompletionRepository, never()).findByTaskIdOrderByCompletedAtDesc(anyLong());
    }

    @Test
    @DisplayName("최근 완료 작업 조회 - 최신 완료 순으로 최대 5건 반환")
    void testFindRecentCompletionsSuccess() {
        OffsetDateTime now = OffsetDateTime.now();
        TaskCompletion completion = TaskCompletion.builder()
                .id(10L)
                .task(task)
                .completedAt(now.minusHours(1))
                .previousDueAt(now.minusDays(1))
                .nextDueAt(now.plusDays(6))
                .build();

        when(taskCompletionRepository.findTop5ByOrderByCompletedAtDesc()).thenReturn(List.of(completion));

        List<RecentTaskCompletionResponse> result = taskService.findRecentCompletions();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTaskId()).isEqualTo(task.getId());
        assertThat(result.get(0).getTaskName()).isEqualTo(task.getName());
        verify(taskCompletionRepository, times(1)).findTop5ByOrderByCompletedAtDesc();
    }

    @Test
    @DisplayName("대시보드 요약 조회 - 상태별 작업 수와 완료 통계를 계산")
    void testGetDashboardSummarySuccess() {
        OffsetDateTime now = OffsetDateTime.now();
        Task overdueTask = Task.builder().id(1L).name("Overdue").everyNDays(2).nextDueAt(now.minusDays(2)).isActive(true).build();
        Task todayTask = Task.builder().id(2L).name("Today").everyNDays(2).nextDueAt(now).isActive(true).build();
        Task upcomingTask = Task.builder().id(3L).name("Upcoming").everyNDays(2).nextDueAt(now.plusDays(2)).isActive(true).build();

        when(taskRepository.findAllByIsActiveTrueOrderByNextDueAtAsc()).thenReturn(List.of(overdueTask, todayTask, upcomingTask));
        when(taskStatusResolver.resolve(eq(overdueTask.getNextDueAt().toInstant()), any())).thenReturn(TaskStatus.OVERDUE);
        when(taskStatusResolver.resolve(eq(todayTask.getNextDueAt().toInstant()), any())).thenReturn(TaskStatus.TODAY);
        when(taskStatusResolver.resolve(eq(upcomingTask.getNextDueAt().toInstant()), any())).thenReturn(TaskStatus.UPCOMING);
        when(taskCompletionRepository.countByCompletedAtBetween(any(), any())).thenReturn(2L);
        when(taskCompletionRepository.countByCompletedAtGreaterThanEqual(any())).thenReturn(8L);

        DashboardSummaryResponse result = taskService.getDashboardSummary();

        assertThat(result.getTotalTasks()).isEqualTo(3);
        assertThat(result.getOverdueTasks()).isEqualTo(1);
        assertThat(result.getTodayTasks()).isEqualTo(1);
        assertThat(result.getUpcomingTasks()).isEqualTo(1);
        assertThat(result.getCompletedToday()).isEqualTo(2);
        assertThat(result.getCompletedLast7Days()).isEqualTo(8);
    }

    @Test
    @DisplayName("인사이트 overview 조회 - 완료율/지연률/평균지연/리스크/작업추세를 계산")
    void testGetInsightsOverviewSuccess() {
        Instant fixedNow = Instant.parse("2026-03-25T00:00:00Z");
        OffsetDateTime now = fixedNow.atOffset(ZoneOffset.UTC);

        Task task1 = Task.builder()
                .id(1L)
                .name("Alpha")
                .everyNDays(7)
                .nextDueAt(now.minusDays(8))     // overdue 7일 초과
                .lastCompletedAt(now.minusDays(2))
                .isActive(true)
                .build();
        Task task2 = Task.builder()
                .id(2L)
                .name("Beta")
                .everyNDays(7)
                .nextDueAt(now.plusDays(1))
                .lastCompletedAt(now.minusDays(40)) // 최근 30일 완료 없음
                .isActive(true)
                .build();
        Task task3 = Task.builder()
                .id(3L)
                .name("Gamma")
                .everyNDays(7)
                .nextDueAt(now.plusDays(1))
                .lastCompletedAt(now.minusDays(1))
                .isActive(true)
                .build();

        TaskCompletion c1 = TaskCompletion.builder()
                .id(101L)
                .task(task1)
                .completedAt(now.minusDays(1))
                .previousDueAt(now.minusDays(2))
                .nextDueAt(now.plusDays(6))
                .build(); // delayed
        TaskCompletion c2 = TaskCompletion.builder()
                .id(102L)
                .task(task2)
                .completedAt(now.minusDays(2))
                .previousDueAt(now.minusDays(2))
                .nextDueAt(now.plusDays(5))
                .build(); // on-time
        TaskCompletion c3 = TaskCompletion.builder()
                .id(103L)
                .task(task1)
                .completedAt(now.minusDays(3))
                .previousDueAt(now.minusDays(4))
                .nextDueAt(now.plusDays(4))
                .build(); // delayed

        when(clock.instant()).thenReturn(fixedNow);
        when(taskRepository.findAllByIsActiveTrueOrderByNextDueAtAsc()).thenReturn(List.of(task1, task2, task3));
        when(taskCompletionRepository.findByCompletedAtGreaterThanEqualAndCompletedAtLessThan(any(), any()))
                .thenReturn(List.of(c1, c2, c3));

        InsightsOverviewResponse result = taskService.getInsightsOverview(30, 5);

        assertThat(result.getPeriodDays()).isEqualTo(30);
        assertThat(result.getPeriodStart()).isEqualTo(now.minusDays(30));
        assertThat(result.getPeriodEnd()).isEqualTo(now);
        assertThat(result.getTimezone()).isEqualTo("Asia/Seoul");
        assertThat(result.getActiveTaskCount()).isEqualTo(3);
        assertThat(result.getCompletedTaskCount()).isEqualTo(2);
        assertThat(result.getCompletionCount()).isEqualTo(3);
        assertThat(result.getDelayedCompletionCount()).isEqualTo(2);
        assertThat(result.getCompletionRatePct()).isEqualTo(66.7);
        assertThat(result.getDelayRatePct()).isEqualTo(66.7);
        assertThat(result.getAverageDelayMinutes()).isEqualTo(1440.0);
        assertThat(result.getRiskyTaskCount()).isEqualTo(2);
        assertThat(result.getRiskyTasks()).hasSize(2);
        assertThat(result.getRiskyTasks())
                .extracting(RiskyTaskInsightResponse::getTaskId)
                .containsExactly(1L, 2L);
        assertThat(result.getRiskyTasks().get(0).getReasons()).containsExactly("OVERDUE_7D_PLUS");
        assertThat(result.getRiskyTasks().get(1).getReasons()).containsExactly("NO_COMPLETION_30D");

        assertThat(result.getTaskTrends()).hasSize(2);
        TaskTrendInsightResponse first = result.getTaskTrends().get(0);
        assertThat(first.getTaskId()).isEqualTo(1L);
        assertThat(first.getTaskName()).isEqualTo("Alpha");
        assertThat(first.getCompletionCount()).isEqualTo(2);
        assertThat(first.getDelayedCount()).isEqualTo(2);
        assertThat(first.getDelayRatePct()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("인사이트 overview 조회 - 활성 작업/완료 이력이 없으면 비율을 0으로 반환")
    void testGetInsightsOverviewNoData() {
        Instant fixedNow = Instant.parse("2026-03-25T00:00:00Z");
        OffsetDateTime now = fixedNow.atOffset(ZoneOffset.UTC);

        when(clock.instant()).thenReturn(fixedNow);
        when(taskRepository.findAllByIsActiveTrueOrderByNextDueAtAsc()).thenReturn(List.of());
        when(taskCompletionRepository.findByCompletedAtGreaterThanEqualAndCompletedAtLessThan(any(), any()))
                .thenReturn(List.of());

        InsightsOverviewResponse result = taskService.getInsightsOverview(30, 5);

        assertThat(result.getPeriodDays()).isEqualTo(30);
        assertThat(result.getPeriodStart()).isEqualTo(now.minusDays(30));
        assertThat(result.getPeriodEnd()).isEqualTo(now);
        assertThat(result.getActiveTaskCount()).isZero();
        assertThat(result.getCompletedTaskCount()).isZero();
        assertThat(result.getCompletionCount()).isZero();
        assertThat(result.getDelayedCompletionCount()).isZero();
        assertThat(result.getCompletionRatePct()).isEqualTo(0.0);
        assertThat(result.getDelayRatePct()).isEqualTo(0.0);
        assertThat(result.getAverageDelayMinutes()).isEqualTo(0.0);
        assertThat(result.getRiskyTaskCount()).isZero();
        assertThat(result.getRiskyTasks()).isEmpty();
        assertThat(result.getTaskTrends()).isEmpty();
    }

    @Test
    @DisplayName("인사이트 overview 조회 - 비활성 Task 완료 이력은 제외하고 completedTaskCount는 distinct 기준으로 집계")
    void testGetInsightsOverviewFiltersInactiveCompletionsAndDistinctTaskCount() {
        Instant fixedNow = Instant.parse("2026-03-25T00:00:00Z");
        OffsetDateTime now = fixedNow.atOffset(ZoneOffset.UTC);

        Task active1 = Task.builder()
                .id(1L)
                .name("Active-1")
                .everyNDays(7)
                .nextDueAt(now.plusDays(1))
                .lastCompletedAt(now.minusDays(1))
                .isActive(true)
                .build();
        Task active2 = Task.builder()
                .id(2L)
                .name("Active-2")
                .everyNDays(7)
                .nextDueAt(now.plusDays(2))
                .lastCompletedAt(now.minusDays(1))
                .isActive(true)
                .build();
        Task inactive = Task.builder()
                .id(99L)
                .name("Inactive")
                .everyNDays(7)
                .nextDueAt(now.plusDays(3))
                .lastCompletedAt(now.minusDays(1))
                .isActive(false)
                .build();

        TaskCompletion active1Delayed = TaskCompletion.builder()
                .id(1001L)
                .task(active1)
                .completedAt(now.minusDays(1))
                .previousDueAt(now.minusDays(2))
                .nextDueAt(now.plusDays(6))
                .build();
        TaskCompletion active1OnTime = TaskCompletion.builder()
                .id(1002L)
                .task(active1)
                .completedAt(now.minusDays(3))
                .previousDueAt(now.minusDays(3))
                .nextDueAt(now.plusDays(4))
                .build();
        TaskCompletion active2OnTime = TaskCompletion.builder()
                .id(1003L)
                .task(active2)
                .completedAt(now.minusDays(2))
                .previousDueAt(now.minusDays(2))
                .nextDueAt(now.plusDays(5))
                .build();
        TaskCompletion inactiveDelayed = TaskCompletion.builder()
                .id(1004L)
                .task(inactive)
                .completedAt(now.minusDays(1))
                .previousDueAt(now.minusDays(2))
                .nextDueAt(now.plusDays(6))
                .build();

        when(clock.instant()).thenReturn(fixedNow);
        when(taskRepository.findAllByIsActiveTrueOrderByNextDueAtAsc()).thenReturn(List.of(active1, active2));
        when(taskCompletionRepository.findByCompletedAtGreaterThanEqualAndCompletedAtLessThan(any(), any()))
                .thenReturn(List.of(active1Delayed, active1OnTime, active2OnTime, inactiveDelayed));

        InsightsOverviewResponse result = taskService.getInsightsOverview(30, 5);

        assertThat(result.getActiveTaskCount()).isEqualTo(2);
        assertThat(result.getCompletedTaskCount()).isEqualTo(2);
        assertThat(result.getCompletionCount()).isEqualTo(3);
        assertThat(result.getDelayedCompletionCount()).isEqualTo(1);
        assertThat(result.getCompletionRatePct()).isEqualTo(100.0);
        assertThat(result.getDelayRatePct()).isEqualTo(33.3);
        assertThat(result.getAverageDelayMinutes()).isEqualTo(1440.0);
        assertThat(result.getRiskyTaskCount()).isZero();
        assertThat(result.getRiskyTasks()).isEmpty();
        assertThat(result.getTaskTrends()).hasSize(2);
        assertThat(result.getTaskTrends())
                .extracting(TaskTrendInsightResponse::getTaskId)
                .containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("인사이트 overview 조회 - 리스크 경계값(7일/30일)은 제외하고 초과분만 집계")
    void testGetInsightsOverviewRiskyThresholdBoundaries() {
        Instant fixedNow = Instant.parse("2026-03-25T00:00:00Z");
        OffsetDateTime now = fixedNow.atOffset(ZoneOffset.UTC);

        Task exactOverdueBoundary = Task.builder()
                .id(1L)
                .name("Exact-7days-overdue")
                .everyNDays(7)
                .nextDueAt(now.minusDays(7))
                .lastCompletedAt(now.minusDays(1))
                .isActive(true)
                .build();
        Task exactRecentBoundary = Task.builder()
                .id(2L)
                .name("Exact-30days-last-complete")
                .everyNDays(7)
                .nextDueAt(now.plusDays(1))
                .lastCompletedAt(now.minusDays(30))
                .isActive(true)
                .build();
        Task overdueRisky = Task.builder()
                .id(3L)
                .name("Overdue-Risky")
                .everyNDays(7)
                .nextDueAt(now.minusDays(8))
                .lastCompletedAt(now.minusDays(1))
                .isActive(true)
                .build();
        Task staleCompletionRisky = Task.builder()
                .id(4L)
                .name("No-Recent-Completion-Risky")
                .everyNDays(7)
                .nextDueAt(now.plusDays(1))
                .lastCompletedAt(now.minusDays(31))
                .isActive(true)
                .build();

        when(clock.instant()).thenReturn(fixedNow);
        when(taskRepository.findAllByIsActiveTrueOrderByNextDueAtAsc())
                .thenReturn(List.of(exactOverdueBoundary, exactRecentBoundary, overdueRisky, staleCompletionRisky));
        when(taskCompletionRepository.findByCompletedAtGreaterThanEqualAndCompletedAtLessThan(any(), any()))
                .thenReturn(List.of());

        InsightsOverviewResponse result = taskService.getInsightsOverview(30, 5);

        assertThat(result.getActiveTaskCount()).isEqualTo(4);
        assertThat(result.getRiskyTaskCount()).isEqualTo(2);
        assertThat(result.getRiskyTasks())
                .extracting(RiskyTaskInsightResponse::getTaskId)
                .containsExactly(3L, 4L);
        assertThat(result.getRiskyTasks().get(0).getReasons()).containsExactly("OVERDUE_7D_PLUS");
        assertThat(result.getRiskyTasks().get(1).getReasons()).containsExactly("NO_COMPLETION_30D");
    }

    @Test
    @DisplayName("인사이트 overview 조회 - 작업 추세는 completion/delayed/taskId 순으로 정렬하고 top으로 제한")
    void testGetInsightsOverviewTrendSortAndTopLimit() {
        Instant fixedNow = Instant.parse("2026-03-25T00:00:00Z");
        OffsetDateTime now = fixedNow.atOffset(ZoneOffset.UTC);

        Task task1 = Task.builder()
                .id(1L)
                .name("Task-1")
                .everyNDays(7)
                .nextDueAt(now.plusDays(1))
                .lastCompletedAt(now.minusDays(1))
                .isActive(true)
                .build();
        Task task2 = Task.builder()
                .id(2L)
                .name("Task-2")
                .everyNDays(7)
                .nextDueAt(now.plusDays(1))
                .lastCompletedAt(now.minusDays(1))
                .isActive(true)
                .build();
        Task task3 = Task.builder()
                .id(3L)
                .name("Task-3")
                .everyNDays(7)
                .nextDueAt(now.plusDays(1))
                .lastCompletedAt(now.minusDays(1))
                .isActive(true)
                .build();

        TaskCompletion t1Delayed = TaskCompletion.builder()
                .id(201L).task(task1)
                .completedAt(now.minusDays(1))
                .previousDueAt(now.minusDays(2))
                .nextDueAt(now.plusDays(6))
                .build();
        TaskCompletion t1OnTime = TaskCompletion.builder()
                .id(202L).task(task1)
                .completedAt(now.minusDays(3))
                .previousDueAt(now.minusDays(3))
                .nextDueAt(now.plusDays(4))
                .build();
        TaskCompletion t2Delayed = TaskCompletion.builder()
                .id(203L).task(task2)
                .completedAt(now.minusDays(2))
                .previousDueAt(now.minusDays(3))
                .nextDueAt(now.plusDays(5))
                .build();
        TaskCompletion t2OnTime = TaskCompletion.builder()
                .id(204L).task(task2)
                .completedAt(now.minusDays(4))
                .previousDueAt(now.minusDays(4))
                .nextDueAt(now.plusDays(3))
                .build();
        TaskCompletion t3Delayed = TaskCompletion.builder()
                .id(205L).task(task3)
                .completedAt(now.minusDays(1))
                .previousDueAt(now.minusDays(2))
                .nextDueAt(now.plusDays(6))
                .build();

        when(clock.instant()).thenReturn(fixedNow);
        when(taskRepository.findAllByIsActiveTrueOrderByNextDueAtAsc()).thenReturn(List.of(task1, task2, task3));
        when(taskCompletionRepository.findByCompletedAtGreaterThanEqualAndCompletedAtLessThan(any(), any()))
                .thenReturn(List.of(t3Delayed, t2OnTime, t1Delayed, t2Delayed, t1OnTime));

        InsightsOverviewResponse result = taskService.getInsightsOverview(30, 2);

        assertThat(result.getTaskTrends()).hasSize(2);
        assertThat(result.getTaskTrends())
                .extracting(TaskTrendInsightResponse::getTaskId)
                .containsExactly(1L, 2L);
        assertThat(result.getTaskTrends())
                .extracting(TaskTrendInsightResponse::getCompletionCount)
                .containsExactly(2L, 2L);
    }

    @Test
    @DisplayName("인사이트 overview 조회 - days 범위를 벗어나면 예외")
    void testGetInsightsOverviewInvalidDays() {
        assertThatThrownBy(() -> taskService.getInsightsOverview(0, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("days는 1~365");
    }

    @Test
    @DisplayName("인사이트 overview 조회 - top 범위를 벗어나면 예외")
    void testGetInsightsOverviewInvalidTop() {
        assertThatThrownBy(() -> taskService.getInsightsOverview(30, 21))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("top은 1~20");
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
    @DisplayName("작업 생성 - startDate 지정 시 응답에 반영")
    void testCreateWithStartDateSuccess() {
        LocalDate startDate = LocalDate.of(2026, 4, 1);
        OffsetDateTime initialDueAt = OffsetDateTime.parse("2026-04-01T00:00:00+09:00");

        CreateTaskRequest request = CreateTaskRequest.builder()
                .name("New Task")
                .everyNDays(5)
                .startDate(startDate)
                .build();

        Task newTask = Task.builder()
                .name("New Task")
                .everyNDays(5)
                .timezone("Asia/Seoul")
                .startDate(startDate)
                .nextDueAt(initialDueAt)
                .isActive(true)
                .build();

        TaskResponse newTaskResponse = TaskResponse.builder()
                .id(2L)
                .name("New Task")
                .everyNDays(5)
                .timezone("Asia/Seoul")
                .startDate(startDate)
                .nextDueAt(initialDueAt)
                .isActive(true)
                .build();

        when(taskMapper.toEntity(request)).thenReturn(newTask);
        when(taskRepository.save(newTask)).thenReturn(newTask);
        when(taskMapper.toResponse(newTask)).thenReturn(newTaskResponse);
        when(taskStatusResolver.resolve(any(Instant.class), any())).thenReturn(TaskStatus.UPCOMING);

        // when
        TaskResponse result = taskService.create(request);

        // then
        assertThat(result.getStartDate()).isEqualTo(startDate);
        assertThat(result.getNextDueAt()).isEqualTo(initialDueAt);
        assertThat(result.getStatus()).isEqualTo(TaskStatus.UPCOMING);
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
    @DisplayName("작업 수정 - startDate 변경 시 nextDueAt을 시작일 00:00으로 재설정")
    void testUpdateWithStartDateResetsNextDueAt() {
        LocalDate startDate = LocalDate.of(2026, 5, 10);
        OffsetDateTime expectedDueAt = OffsetDateTime.parse("2026-05-10T00:00:00+09:00");

        UpdateTaskRequest request = UpdateTaskRequest.builder()
                .startDate(startDate)
                .build();

        TaskResponse updatedResponse = TaskResponse.builder()
                .id(1L)
                .name(task.getName())
                .everyNDays(task.getEveryNDays())
                .timezone(task.getTimezone())
                .startDate(startDate)
                .nextDueAt(expectedDueAt)
                .isActive(task.getIsActive())
                .build();

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskMapper.toResponse(task)).thenReturn(updatedResponse);
        when(taskStatusResolver.resolve(any(Instant.class), any())).thenReturn(TaskStatus.TODAY);

        // when
        TaskResponse result = taskService.update(1L, request);

        // then
        assertThat(task.getStartDate()).isEqualTo(startDate);
        assertThat(task.getNextDueAt()).isEqualTo(expectedDueAt);
        assertThat(result.getStartDate()).isEqualTo(startDate);
        assertThat(result.getStatus()).isEqualTo(TaskStatus.TODAY);
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

    @Test
    @DisplayName("작업 완료 - 성공 (completedAt·lastCompletedAt·nextDueAt 갱신 및 status 포함)")
    void testCompleteSuccess() {
        // given
        Instant fixedNow = Instant.parse("2026-03-05T12:00:00Z");
        OffsetDateTime fixedOdt = fixedNow.atOffset(ZoneOffset.UTC);

        OffsetDateTime beforeNow = fixedOdt.minusDays(1);
        Task activeTask = Task.builder()
                .id(1L)
                .name("Test Task")
                .everyNDays(7)
                .timezone("Asia/Seoul")
                .nextDueAt(beforeNow)   // OVERDUE 상태
                .isActive(true)
                .createdAt(beforeNow)
                .updatedAt(beforeNow)
                .build();

        TaskResponse completedResponse = TaskResponse.builder()
                .id(1L)
                .name("Test Task")
                .everyNDays(7)
                .nextDueAt(fixedOdt.plusDays(7))
                .lastCompletedAt(fixedOdt)
                .completedAt(fixedOdt)
                .isActive(true)
                .build();

        when(clock.instant()).thenReturn(fixedNow);
        when(taskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(activeTask));
        when(taskMapper.toResponse(activeTask)).thenReturn(completedResponse);
        when(taskStatusResolver.resolve(any(Instant.class), any())).thenReturn(TaskStatus.UPCOMING);

        // when
        TaskResponse result = taskService.complete(1L);

        // then
        // completedAt = lastCompletedAt = fixedNow(UTC)
        assertThat(activeTask.getCompletedAt()).isEqualTo(fixedOdt);
        assertThat(activeTask.getLastCompletedAt()).isEqualTo(fixedOdt);
        // nextDueAt = fixedNow + 7일
        assertThat(activeTask.getNextDueAt()).isEqualTo(fixedOdt.plusDays(7));
        // 응답 status 포함
        assertThat(result.getStatus()).isEqualTo(TaskStatus.UPCOMING);
        verify(taskCompletionRepository, times(1)).save(argThat(completion ->
                completion.getTask().equals(activeTask)
                        && completion.getCompletedAt().isEqual(fixedOdt)
                        && completion.getPreviousDueAt().isEqual(beforeNow)
                        && completion.getNextDueAt().isEqual(fixedOdt.plusDays(7))
        ));
        verify(clock, times(2)).instant();
        verify(taskRepository, times(1)).findByIdForUpdate(1L);
        verify(taskMapper, times(1)).toResponse(activeTask);
    }

    @Test
    @DisplayName("작업 완료 - 존재하지 않음 (404)")
    void testCompleteNotFound() {
        // given
        when(taskRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> taskService.complete(999L))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining("작업을 찾을 수 없습니다")
                .hasMessageContaining("999");

        verify(taskRepository, times(1)).findByIdForUpdate(999L);
        verify(taskMapper, never()).toResponse(any());
    }

    @Test
    @DisplayName("작업 완료 - 비활성 Task (409)")
    void testCompleteInactiveTask() {
        // given
        OffsetDateTime now = OffsetDateTime.now();
        Task inactiveTask = Task.builder()
                .id(1L)
                .name("Inactive Task")
                .everyNDays(7)
                .nextDueAt(now.plusDays(7))
                .isActive(false)   // 비활성
                .createdAt(now)
                .updatedAt(now)
                .build();

        when(taskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(inactiveTask));

        // when & then
        assertThatThrownBy(() -> taskService.complete(1L))
                .isInstanceOf(TaskInactiveException.class)
                .hasMessageContaining("비활성화된 작업입니다")
                .hasMessageContaining("1");

        verify(taskRepository, times(1)).findByIdForUpdate(1L);
        // complete() 이후 로직은 실행되지 않아야 함
        verify(taskMapper, never()).toResponse(any());
        verify(taskCompletionRepository, never()).save(any());
    }

    @Test
    @DisplayName("작업 완료 - 2초 이내 중복 완료 시도 시 409")
    void testCompleteDuplicateWithinCooldown() {
        // given
        Instant fixedNow = Instant.parse("2026-03-05T12:00:01Z");
        OffsetDateTime lastCompleted = Instant.parse("2026-03-05T12:00:00Z").atOffset(ZoneOffset.UTC); // 1초 전

        Task recentlyCompletedTask = Task.builder()
                .id(1L)
                .name("Test Task")
                .everyNDays(7)
                .nextDueAt(lastCompleted.plusDays(7))
                .lastCompletedAt(lastCompleted)   // 1초 전에 완료됨
                .isActive(true)
                .createdAt(lastCompleted)
                .updatedAt(lastCompleted)
                .build();

        when(clock.instant()).thenReturn(fixedNow);
        when(taskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(recentlyCompletedTask));

        // when & then
        assertThatThrownBy(() -> taskService.complete(1L))
                .isInstanceOf(TaskRecentlyCompletedException.class)
                .hasMessageContaining("최근에 이미 완료된 작업입니다")
                .hasMessageContaining("1");

        verify(taskRepository, times(1)).findByIdForUpdate(1L);
        verify(taskMapper, never()).toResponse(any());
        verify(taskCompletionRepository, never()).save(any());
    }

    @Test
    @DisplayName("작업 완료 - 2초 이후 재완료는 정상 처리")
    void testCompleteAfterCooldown() {
        // given
        Instant fixedNow = Instant.parse("2026-03-05T12:00:03Z");
        OffsetDateTime fixedOdt = fixedNow.atOffset(ZoneOffset.UTC);
        OffsetDateTime lastCompleted = Instant.parse("2026-03-05T12:00:00Z").atOffset(ZoneOffset.UTC); // 3초 전

        Task previouslyCompletedTask = Task.builder()
                .id(1L)
                .name("Test Task")
                .everyNDays(7)
                .nextDueAt(lastCompleted.plusDays(7))
                .lastCompletedAt(lastCompleted)   // 3초 전에 완료됨
                .isActive(true)
                .createdAt(lastCompleted)
                .updatedAt(lastCompleted)
                .build();

        TaskResponse response = TaskResponse.builder()
                .id(1L)
                .name("Test Task")
                .everyNDays(7)
                .nextDueAt(fixedOdt.plusDays(7))
                .lastCompletedAt(fixedOdt)
                .completedAt(fixedOdt)
                .isActive(true)
                .build();

        when(clock.instant()).thenReturn(fixedNow);
        when(taskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(previouslyCompletedTask));
        when(taskMapper.toResponse(previouslyCompletedTask)).thenReturn(response);
        when(taskStatusResolver.resolve(any(Instant.class), any())).thenReturn(TaskStatus.UPCOMING);

        // when
        TaskResponse result = taskService.complete(1L);

        // then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.UPCOMING);
        assertThat(previouslyCompletedTask.getCompletedAt()).isEqualTo(fixedOdt);
        assertThat(previouslyCompletedTask.getLastCompletedAt()).isEqualTo(fixedOdt);
        assertThat(previouslyCompletedTask.getNextDueAt()).isEqualTo(fixedOdt.plusDays(7));
        verify(taskCompletionRepository, times(1)).save(any(TaskCompletion.class));
        verify(taskRepository, times(1)).findByIdForUpdate(1L);
        verify(taskMapper, times(1)).toResponse(previouslyCompletedTask);
    }
}
