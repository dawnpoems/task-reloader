package com.yegkim.task_reloader_api.common.time;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TimeWindow 단위테스트")
class TimeWindowTest {

    @Test
    @DisplayName("ofKst(clock) - todayStartUtc는 KST 기준 오늘 00:00:00의 UTC Instant여야 한다")
    void testOfKstTodayStart() {
        // given
        ZoneId kst = ZoneId.of("Asia/Seoul");
        Instant fixedNow = Instant.parse("2026-03-05T03:21:00Z");
        Clock fixedClock = Clock.fixed(fixedNow, kst);
        Instant expected = ZonedDateTime.now(fixedClock.withZone(kst)).toLocalDate().atStartOfDay(kst).toInstant();

        // when
        TimeWindow window = TimeWindow.ofKst(fixedClock);

        // then
        assertThat(window.getTodayStartUtc()).isEqualTo(expected);
    }

    @Test
    @DisplayName("ofKst(clock) - tomorrowStartUtc는 todayStartUtc로부터 정확히 24시간 후여야 한다")
    void testOfKstTomorrowStartIs24HoursAfterToday() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-05T03:21:00Z"), ZoneId.of("Asia/Seoul"));

        // when
        TimeWindow window = TimeWindow.ofKst(fixedClock);

        // then
        Duration diff = Duration.between(window.getTodayStartUtc(), window.getTomorrowStartUtc());
        assertThat(diff).isEqualTo(Duration.ofHours(24));
    }

    @Test
    @DisplayName("of(zone, clock) - 지정한 ZoneId 기준으로 todayStart가 계산되어야 한다")
    void testOfWithCustomZone() {
        // given
        ZoneId utc = ZoneId.of("UTC");
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-05T03:21:00Z"), utc);
        Instant expected = ZonedDateTime.now(fixedClock.withZone(utc)).toLocalDate().atStartOfDay(utc).toInstant();

        // when
        TimeWindow window = TimeWindow.of(utc, fixedClock);

        // then
        assertThat(window.getTodayStartUtc()).isEqualTo(expected);
    }

    @Test
    @DisplayName("todayStartUtc는 tomorrowStartUtc보다 이전이어야 한다")
    void testTodayStartIsBeforeTomorrowStart() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-05T03:21:00Z"), ZoneId.of("Asia/Seoul"));

        // when
        TimeWindow window = TimeWindow.ofKst(fixedClock);

        // then
        assertThat(window.getTodayStartUtc()).isBefore(window.getTomorrowStartUtc());
    }

    @Test
    @DisplayName("clock 기준 현재 시각은 todayStartUtc 이상, tomorrowStartUtc 미만이어야 한다")
    void testNowIsBetweenTodayAndTomorrow() {
        // given
        ZoneId kst = ZoneId.of("Asia/Seoul");
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-05T03:21:00Z"), kst);
        TimeWindow window = TimeWindow.ofKst(fixedClock);
        Instant now = fixedClock.instant();

        // then
        assertThat(now).isAfterOrEqualTo(window.getTodayStartUtc());
        assertThat(now).isBefore(window.getTomorrowStartUtc());
    }
}
