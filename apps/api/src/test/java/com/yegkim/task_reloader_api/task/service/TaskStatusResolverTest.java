package com.yegkim.task_reloader_api.task.service;

import com.yegkim.task_reloader_api.common.time.TimeWindow;
import com.yegkim.task_reloader_api.task.entity.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TaskStatusResolver 단위테스트")
class TaskStatusResolverTest {

    private TaskStatusResolver resolver;

    // 테스트용 고정 TimeWindow: KST 2026-03-05 00:00 기준
    // KST 2026-03-05 00:00 = UTC 2026-03-04 15:00
    // KST 2026-03-06 00:00 = UTC 2026-03-05 15:00
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Instant TODAY_START =
            ZonedDateTime.of(2026, 3, 5, 0, 0, 0, 0, KST).toInstant();  // UTC 2026-03-04 15:00
    private static final Instant TOMORROW_START =
            ZonedDateTime.of(2026, 3, 6, 0, 0, 0, 0, KST).toInstant();  // UTC 2026-03-05 15:00

    private TimeWindow window;

    @BeforeEach
    void setUp() {
        resolver = new TaskStatusResolver();
        // 고정된 경계값을 갖는 TimeWindow를 직접 생성
        window = TimeWindow.of(TODAY_START, TOMORROW_START);
    }

    @Test
    @DisplayName("nextDueAt이 todayStart 1초 전이면 OVERDUE")
    void overdue_justBeforeTodayStart() {
        Instant nextDueAt = TODAY_START.minusSeconds(1);
        assertThat(resolver.resolve(nextDueAt, window)).isEqualTo(TaskStatus.OVERDUE);
    }

    @Test
    @DisplayName("nextDueAt이 todayStart와 같으면 TODAY (경계 포함)")
    void today_exactlyAtTodayStart() {
        assertThat(resolver.resolve(TODAY_START, window)).isEqualTo(TaskStatus.TODAY);
    }

    @Test
    @DisplayName("nextDueAt이 오늘 범위 중간이면 TODAY")
    void today_midDay() {
        Instant midDay = TODAY_START.plusSeconds(3600 * 12); // +12시간
        assertThat(resolver.resolve(midDay, window)).isEqualTo(TaskStatus.TODAY);
    }

    @Test
    @DisplayName("nextDueAt이 tomorrowStart 1초 전이면 TODAY (경계 직전)")
    void today_justBeforeTomorrowStart() {
        Instant nextDueAt = TOMORROW_START.minusSeconds(1);
        assertThat(resolver.resolve(nextDueAt, window)).isEqualTo(TaskStatus.TODAY);
    }

    @Test
    @DisplayName("nextDueAt이 tomorrowStart와 같으면 UPCOMING (경계 제외)")
    void upcoming_exactlyAtTomorrowStart() {
        assertThat(resolver.resolve(TOMORROW_START, window)).isEqualTo(TaskStatus.UPCOMING);
    }

    @Test
    @DisplayName("nextDueAt이 tomorrowStart 이후면 UPCOMING")
    void upcoming_afterTomorrowStart() {
        Instant nextDueAt = TOMORROW_START.plusSeconds(3600 * 24);
        assertThat(resolver.resolve(nextDueAt, window)).isEqualTo(TaskStatus.UPCOMING);
    }
}


