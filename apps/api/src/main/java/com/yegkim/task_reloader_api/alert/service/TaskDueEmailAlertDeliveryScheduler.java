package com.yegkim.task_reloader_api.alert.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "spring.mail", name = "host")
@RequiredArgsConstructor
public class TaskDueEmailAlertDeliveryScheduler {

    private final TaskDueEmailAlertDeliveryService deliveryService;

    @Value("${task-due-email-alert.scheduler.batch-size:100}")
    private int batchSize;

    @Value("${task-due-email-alert.scheduler.enabled:true}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${task-due-email-alert.scheduler.poll-interval-ms:60000}")
    public void deliverDueAlerts() {
        if (!enabled) {
            return;
        }

        try {
            int processedCount = deliveryService.deliverDueSettings(batchSize);
            if (processedCount > 0) {
                log.info("Task due email alert delivery processed count={}", processedCount);
            }
        } catch (RuntimeException ex) {
            log.error("Task due email alert delivery scheduler failed", ex);
        }
    }
}
