package com.yegkim.task_reloader_api.alert.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Component
public class TaskDueEmailAlertNextSendAtCalculator {

    private final Clock clock;

    public TaskDueEmailAlertNextSendAtCalculator(Clock clock) {
        this.clock = clock;
    }

    public OffsetDateTime calculate(boolean enabled, LocalTime sendTime, String timezone) {
        if (!enabled) {
            return null;
        }

        ZoneId zoneId = ZoneId.of(timezone);
        ZonedDateTime now = ZonedDateTime.ofInstant(clock.instant(), zoneId);
        LocalDate localDate = now.toLocalDate();
        ZonedDateTime candidate = localDate.atTime(sendTime).atZone(zoneId);
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusDays(1);
        }

        return candidate.withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime();
    }
}
