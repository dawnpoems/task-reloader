package com.yegkim.task_reloader_api.alert.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TaskDueEmailAlertNextSendAtCalculator 단위테스트")
class TaskDueEmailAlertNextSendAtCalculatorTest {

    @Test
    @DisplayName("알림이 비활성화되어 있으면 다음 발송 시각은 null이다")
    void calculateDisabled() {
        TaskDueEmailAlertNextSendAtCalculator calculator = calculator("2026-05-25T00:10:00Z");

        OffsetDateTime nextSendAt = calculator.calculate(false, LocalTime.of(9, 0), "Asia/Seoul");

        assertThat(nextSendAt).isNull();
    }

    @Test
    @DisplayName("오늘 발송 시각이 아직 지나지 않았으면 오늘 시각을 UTC로 반환한다")
    void calculateTodaySendTime() {
        TaskDueEmailAlertNextSendAtCalculator calculator = calculator("2026-05-25T00:10:00Z");

        OffsetDateTime nextSendAt = calculator.calculate(true, LocalTime.of(9, 30), "Asia/Seoul");

        assertThat(nextSendAt).isEqualTo(OffsetDateTime.parse("2026-05-25T00:30:00Z"));
    }

    @Test
    @DisplayName("오늘 발송 시각이 이미 지났으면 다음 날 시각을 UTC로 반환한다")
    void calculateTomorrowSendTime() {
        TaskDueEmailAlertNextSendAtCalculator calculator = calculator("2026-05-25T00:10:00Z");

        OffsetDateTime nextSendAt = calculator.calculate(true, LocalTime.of(9, 0), "Asia/Seoul");

        assertThat(nextSendAt).isEqualTo(OffsetDateTime.parse("2026-05-26T00:00:00Z"));
    }

    @Test
    @DisplayName("사용자 타임존 기준 발송 시각을 UTC로 변환한다")
    void calculateWithUserTimezone() {
        TaskDueEmailAlertNextSendAtCalculator calculator = calculator("2026-05-25T12:00:00Z");

        OffsetDateTime nextSendAt = calculator.calculate(true, LocalTime.of(9, 0), "America/New_York");

        assertThat(nextSendAt).isEqualTo(OffsetDateTime.parse("2026-05-25T13:00:00Z"));
    }

    private TaskDueEmailAlertNextSendAtCalculator calculator(String instant) {
        return new TaskDueEmailAlertNextSendAtCalculator(
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC)
        );
    }
}
