package com.yegkim.task_reloader_api.common.time;

import lombok.Getter;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Getter
public class TimeWindow {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");

    private final Instant todayStartUtc;
    private final Instant tomorrowStartUtc;

    private TimeWindow(ZoneId zone, Clock clock) {
        ZonedDateTime todayStart = ZonedDateTime.ofInstant(clock.instant(), zone).toLocalDate().atStartOfDay(zone);
        this.todayStartUtc = todayStart.toInstant();
        this.tomorrowStartUtc = todayStart.plusDays(1).toInstant();
    }

    /** 테스트 등 경계값을 직접 지정해야 할 때 사용 */
    private TimeWindow(Instant todayStartUtc, Instant tomorrowStartUtc) {
        this.todayStartUtc = todayStartUtc;
        this.tomorrowStartUtc = tomorrowStartUtc;
    }

    public static TimeWindow of(ZoneId zone) {
        return new TimeWindow(zone, Clock.system(zone));
    }

    public static TimeWindow of(ZoneId zone, Clock clock) {
        return new TimeWindow(zone, clock);
    }

    /** 테스트용: 경계 Instant를 직접 주입 */
    public static TimeWindow of(Instant todayStartUtc, Instant tomorrowStartUtc) {
        return new TimeWindow(todayStartUtc, tomorrowStartUtc);
    }

    public static TimeWindow ofKst() {
        return new TimeWindow(DEFAULT_ZONE, Clock.system(DEFAULT_ZONE));
    }

    public static TimeWindow ofKst(Clock clock) {
        return new TimeWindow(DEFAULT_ZONE, clock);
    }
}
