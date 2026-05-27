package com.yegkim.task_reloader_api.alert.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record TaskDueEmailAlertSettingsResponse(
        boolean enabled,
        LocalTime sendTime,
        String timezone,
        LocalDate lastSentLocalDate,
        String suggestedEmail,
        int maxRecipientCount,
        TaskDueEmailAlertLastDeliveryResponse lastDelivery
) {
    public TaskDueEmailAlertSettingsResponse(
            boolean enabled,
            LocalTime sendTime,
            String timezone,
            LocalDate lastSentLocalDate,
            String suggestedEmail,
            int maxRecipientCount
    ) {
        this(enabled, sendTime, timezone, lastSentLocalDate, suggestedEmail, maxRecipientCount, null);
    }
}
