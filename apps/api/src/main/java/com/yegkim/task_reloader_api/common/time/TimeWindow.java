package com.yegkim.task_reloader_api.common.time;

import lombok.Getter;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Getter
public class TimeWindow {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");

    private final Instant todayStartUtc;
    private final Instant tomorrowStartUtc;

    private TimeWindow(ZoneId zone) {
        ZonedDateTime todayStart = ZonedDateTime.now(zone).toLocalDate().atStartOfDay(zone);
        this.todayStartUtc = todayStart.toInstant();
        this.tomorrowStartUtc = todayStart.plusDays(1).toInstant();
    }

    public static TimeWindow of(ZoneId zone) {
        return new TimeWindow(zone);
    }

    public static TimeWindow ofKst() {
        return new TimeWindow(DEFAULT_ZONE);
    }
}
