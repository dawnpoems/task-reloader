package com.yegkim.task_reloader_api.alert.service;

import com.yegkim.task_reloader_api.alert.dto.TaskDueEmailAlertSummary;
import com.yegkim.task_reloader_api.task.entity.Task;
import com.yegkim.task_reloader_api.task.repository.TaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("TaskDueEmailAlertAggregationService 단위테스트")
class TaskDueEmailAlertAggregationServiceTest {

    private static final long USER_ID = 1L;

    private final TaskRepository taskRepository = mock(TaskRepository.class);

    @Test
    @DisplayName("사용자 타임존 기준 경계로 overdue와 due today를 조회하고 요약한다")
    void aggregateByUserTimezone() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-24T14:30:00Z"), ZoneOffset.UTC);
        TaskDueEmailAlertAggregationService service =
                new TaskDueEmailAlertAggregationService(taskRepository, fixedClock);

        Task overdue = task(10L, "Overdue", "2026-05-23T14:59:00Z");
        Task dueToday = task(20L, "Due Today", "2026-05-23T15:00:00Z");
        when(taskRepository.findAllByUserIdAndIsActiveTrueAndNextDueAtBeforeOrderByNextDueAtAsc(
                USER_ID,
                OffsetDateTime.parse("2026-05-23T15:00:00Z")
        )).thenReturn(List.of(overdue));
        when(taskRepository.findAllByUserIdAndIsActiveTrueAndNextDueAtGreaterThanEqualAndNextDueAtLessThanOrderByNextDueAtAsc(
                USER_ID,
                OffsetDateTime.parse("2026-05-23T15:00:00Z"),
                OffsetDateTime.parse("2026-05-24T15:00:00Z")
        )).thenReturn(List.of(dueToday));

        TaskDueEmailAlertSummary summary = service.aggregate(USER_ID, "Asia/Seoul");

        assertThat(summary.userId()).isEqualTo(USER_ID);
        assertThat(summary.localDate()).isEqualTo(LocalDate.of(2026, 5, 24));
        assertThat(summary.timezone()).isEqualTo("Asia/Seoul");
        assertThat(summary.overdueCount()).isEqualTo(1);
        assertThat(summary.dueTodayCount()).isEqualTo(1);
        assertThat(summary.totalCount()).isEqualTo(2);
        assertThat(summary.isEmpty()).isFalse();
        assertThat(summary.overdueTasks().get(0).dueDate()).isEqualTo(LocalDate.of(2026, 5, 23));
        assertThat(summary.dueTodayTasks().get(0).dueDate()).isEqualTo(LocalDate.of(2026, 5, 24));
    }

    @Test
    @DisplayName("타임존이 바뀌면 조회 경계와 로컬 날짜도 해당 타임존 기준으로 계산한다")
    void aggregateWithDifferentTimezone() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-24T02:00:00Z"), ZoneOffset.UTC);
        TaskDueEmailAlertAggregationService service =
                new TaskDueEmailAlertAggregationService(taskRepository, fixedClock);
        when(taskRepository.findAllByUserIdAndIsActiveTrueAndNextDueAtBeforeOrderByNextDueAtAsc(
                USER_ID,
                OffsetDateTime.parse("2026-05-23T04:00:00Z")
        )).thenReturn(List.of());
        when(taskRepository.findAllByUserIdAndIsActiveTrueAndNextDueAtGreaterThanEqualAndNextDueAtLessThanOrderByNextDueAtAsc(
                USER_ID,
                OffsetDateTime.parse("2026-05-23T04:00:00Z"),
                OffsetDateTime.parse("2026-05-24T04:00:00Z")
        )).thenReturn(List.of());

        TaskDueEmailAlertSummary summary = service.aggregate(USER_ID, "America/New_York");

        assertThat(summary.localDate()).isEqualTo(LocalDate.of(2026, 5, 23));
        assertThat(summary.timezone()).isEqualTo("America/New_York");
        assertThat(summary.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("repository 정렬 결과 순서를 유지해 메일용 항목을 만든다")
    void aggregateKeepsRepositoryOrder() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-24T14:30:00Z"), ZoneOffset.UTC);
        TaskDueEmailAlertAggregationService service =
                new TaskDueEmailAlertAggregationService(taskRepository, fixedClock);
        Task older = task(10L, "Older", "2026-05-21T00:00:00Z");
        Task newer = task(20L, "Newer", "2026-05-22T00:00:00Z");
        when(taskRepository.findAllByUserIdAndIsActiveTrueAndNextDueAtBeforeOrderByNextDueAtAsc(
                USER_ID,
                OffsetDateTime.parse("2026-05-23T15:00:00Z")
        )).thenReturn(List.of(older, newer));
        when(taskRepository.findAllByUserIdAndIsActiveTrueAndNextDueAtGreaterThanEqualAndNextDueAtLessThanOrderByNextDueAtAsc(
                USER_ID,
                OffsetDateTime.parse("2026-05-23T15:00:00Z"),
                OffsetDateTime.parse("2026-05-24T15:00:00Z")
        )).thenReturn(List.of());

        TaskDueEmailAlertSummary summary = service.aggregate(USER_ID, "Asia/Seoul");

        assertThat(summary.overdueTasks())
                .extracting("taskId")
                .containsExactly(10L, 20L);
    }

    @Test
    @DisplayName("집계 결과가 0건이면 isEmpty가 true다")
    void aggregateEmptyResult() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-24T14:30:00Z"), ZoneOffset.UTC);
        TaskDueEmailAlertAggregationService service =
                new TaskDueEmailAlertAggregationService(taskRepository, fixedClock);
        when(taskRepository.findAllByUserIdAndIsActiveTrueAndNextDueAtBeforeOrderByNextDueAtAsc(
                USER_ID,
                OffsetDateTime.parse("2026-05-23T15:00:00Z")
        )).thenReturn(List.of());
        when(taskRepository.findAllByUserIdAndIsActiveTrueAndNextDueAtGreaterThanEqualAndNextDueAtLessThanOrderByNextDueAtAsc(
                USER_ID,
                OffsetDateTime.parse("2026-05-23T15:00:00Z"),
                OffsetDateTime.parse("2026-05-24T15:00:00Z")
        )).thenReturn(List.of());

        TaskDueEmailAlertSummary summary = service.aggregate(USER_ID, "Asia/Seoul");

        assertThat(summary.isEmpty()).isTrue();
        assertThat(summary.totalCount()).isZero();
    }

    @Test
    @DisplayName("공백 타임존은 거부한다")
    void rejectBlankTimezone() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-24T14:30:00Z"), ZoneOffset.UTC);
        TaskDueEmailAlertAggregationService service =
                new TaskDueEmailAlertAggregationService(taskRepository, fixedClock);

        assertThatThrownBy(() -> service.aggregate(USER_ID, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("timezone은 비워둘 수 없습니다.");
    }

    @Test
    @DisplayName("지원하지 않는 타임존은 거부한다")
    void rejectInvalidTimezone() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-24T14:30:00Z"), ZoneOffset.UTC);
        TaskDueEmailAlertAggregationService service =
                new TaskDueEmailAlertAggregationService(taskRepository, fixedClock);

        assertThatThrownBy(() -> service.aggregate(USER_ID, "Not/A_Zone"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("지원하지 않는 timezone입니다.");
    }

    @Test
    @DisplayName("repository에는 UTC 경계값을 전달한다")
    void passUtcBoundariesToRepository() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-24T14:30:00Z"), ZoneOffset.UTC);
        TaskDueEmailAlertAggregationService service =
                new TaskDueEmailAlertAggregationService(taskRepository, fixedClock);
        when(taskRepository.findAllByUserIdAndIsActiveTrueAndNextDueAtBeforeOrderByNextDueAtAsc(
                USER_ID,
                OffsetDateTime.parse("2026-05-23T15:00:00Z")
        )).thenReturn(List.of());
        when(taskRepository.findAllByUserIdAndIsActiveTrueAndNextDueAtGreaterThanEqualAndNextDueAtLessThanOrderByNextDueAtAsc(
                USER_ID,
                OffsetDateTime.parse("2026-05-23T15:00:00Z"),
                OffsetDateTime.parse("2026-05-24T15:00:00Z")
        )).thenReturn(List.of());

        service.aggregate(USER_ID, "Asia/Seoul");

        ArgumentCaptor<OffsetDateTime> todayStartCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(taskRepository).findAllByUserIdAndIsActiveTrueAndNextDueAtBeforeOrderByNextDueAtAsc(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                todayStartCaptor.capture()
        );
        assertThat(todayStartCaptor.getValue()).isEqualTo(OffsetDateTime.parse("2026-05-23T15:00:00Z"));
    }

    private Task task(Long id, String name, String nextDueAt) {
        return Task.builder()
                .id(id)
                .userId(USER_ID)
                .name(name)
                .everyNDays(1)
                .nextDueAt(OffsetDateTime.parse(nextDueAt))
                .isActive(true)
                .build();
    }
}
