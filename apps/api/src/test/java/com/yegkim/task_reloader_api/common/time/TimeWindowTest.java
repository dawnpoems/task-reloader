package com.yegkim.task_reloader_api.common.time;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TimeWindow 단위테스트")
class TimeWindowTest {

    @Test
    @DisplayName("ofKst() - todayStartUtc는 KST 기준 오늘 00:00:00의 UTC Instant여야 한다")
    void testOfKstTodayStart() {
        // given
        ZoneId kst = ZoneId.of("Asia/Seoul");
        Instant expected = ZonedDateTime.now(kst).toLocalDate().atStartOfDay(kst).toInstant();

        // when
        TimeWindow window = TimeWindow.ofKst();

        // then
        assertThat(window.getTodayStartUtc()).isEqualTo(expected);
    }

    @Test
    @DisplayName("ofKst() - tomorrowStartUtc는 todayStartUtc로부터 정확히 24시간 후여야 한다")
    void testOfKstTomorrowStartIs24HoursAfterToday() {
        // when
        TimeWindow window = TimeWindow.ofKst();

        // then
        Duration diff = Duration.between(window.getTodayStartUtc(), window.getTomorrowStartUtc());
        assertThat(diff).isEqualTo(Duration.ofHours(24));
    }

    @Test
    @DisplayName("of(zone) - 지정한 ZoneId 기준으로 todayStart가 계산되어야 한다")
    void testOfWithCustomZone() {
        // given
        ZoneId utc = ZoneId.of("UTC");
        Instant expected = ZonedDateTime.now(utc).toLocalDate().atStartOfDay(utc).toInstant();

        // when
        TimeWindow window = TimeWindow.of(utc);

        // then
        assertThat(window.getTodayStartUtc()).isEqualTo(expected);
    }

    @Test
    @DisplayName("todayStartUtc는 tomorrowStartUtc보다 이전이어야 한다")
    void testTodayStartIsBeforeTomorrowStart() {
        // when
        TimeWindow window = TimeWindow.ofKst();

        // then
        assertThat(window.getTodayStartUtc()).isBefore(window.getTomorrowStartUtc());
    }

    @Test
    @DisplayName("now()는 todayStartUtc 이상, tomorrowStartUtc 미만이어야 한다")
    void testNowIsBetweenTodayAndTomorrow() {
        // given
        ZoneId kst = ZoneId.of("Asia/Seoul");
        TimeWindow window = TimeWindow.ofKst();
        Instant now = ZonedDateTime.now(kst).toInstant();

        // then
        assertThat(now).isAfterOrEqualTo(window.getTodayStartUtc());
        assertThat(now).isBefore(window.getTomorrowStartUtc());
    }
}

