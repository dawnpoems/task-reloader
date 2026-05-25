package com.yegkim.task_reloader_api.alert.service;

import com.yegkim.task_reloader_api.alert.dto.TaskDueEmailAlertSummary;
import com.yegkim.task_reloader_api.alert.entity.TaskDueEmailAlertDeliveryLog;
import com.yegkim.task_reloader_api.alert.entity.TaskDueEmailAlertDeliveryStatus;
import com.yegkim.task_reloader_api.alert.entity.TaskDueEmailAlertRecipient;
import com.yegkim.task_reloader_api.alert.entity.TaskDueEmailAlertSetting;
import com.yegkim.task_reloader_api.alert.mail.TaskDueEmailAlertMailSender;
import com.yegkim.task_reloader_api.alert.repository.TaskDueEmailAlertDeliveryLogRepository;
import com.yegkim.task_reloader_api.alert.repository.TaskDueEmailAlertRecipientRepository;
import com.yegkim.task_reloader_api.alert.repository.TaskDueEmailAlertSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "spring.mail", name = "host")
@RequiredArgsConstructor
public class TaskDueEmailAlertDeliveryService {

    private static final int MAX_ATTEMPT_COUNT = 2;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;
    private static final String NO_RECIPIENTS_MESSAGE = "등록된 수신 이메일이 없습니다.";
    private static final String EMPTY_SUMMARY_MESSAGE = "오늘 마감 또는 지난 작업이 없습니다.";

    private final TaskDueEmailAlertSettingRepository settingRepository;
    private final TaskDueEmailAlertRecipientRepository recipientRepository;
    private final TaskDueEmailAlertDeliveryLogRepository deliveryLogRepository;
    private final TaskDueEmailAlertAggregationService aggregationService;
    private final TaskDueEmailAlertMailSender mailSender;
    private final TaskDueEmailAlertNextSendAtCalculator nextSendAtCalculator;
    private final Clock clock;

    @Transactional
    public int deliverDueSettings(int batchSize) {
        OffsetDateTime nowUtc = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        List<TaskDueEmailAlertSetting> dueSettings = settingRepository.findDueSettingsForUpdate(nowUtc, batchSize);
        dueSettings.forEach(this::deliverOne);
        return dueSettings.size();
    }

    private void deliverOne(TaskDueEmailAlertSetting setting) {
        LocalDate localDate = currentLocalDate(setting.getTimezone());
        if (deliveryLogRepository.findByUserIdAndLocalDateForUpdate(setting.getUserId(), localDate).isPresent()) {
            advanceNextSendAt(setting);
            log.info(
                    "Task due email alert skipped because delivery log already exists userId={} localDate={}",
                    setting.getUserId(),
                    localDate
            );
            return;
        }

        List<String> recipients = recipientRepository.findAllByUserIdOrderByCreatedAtAsc(setting.getUserId()).stream()
                .map(TaskDueEmailAlertRecipient::getEmail)
                .toList();
        TaskDueEmailAlertSummary summary = aggregationService.aggregate(setting.getUserId(), setting.getTimezone());

        if (recipients.isEmpty()) {
            recordDeliveryLog(
                    setting,
                    localDate,
                    TaskDueEmailAlertDeliveryStatus.SKIPPED,
                    1,
                    0,
                    NO_RECIPIENTS_MESSAGE
            );
            advanceNextSendAt(setting);
            return;
        }
        if (summary.isEmpty()) {
            recordDeliveryLog(
                    setting,
                    localDate,
                    TaskDueEmailAlertDeliveryStatus.SKIPPED,
                    1,
                    recipients.size(),
                    EMPTY_SUMMARY_MESSAGE
            );
            advanceNextSendAt(setting);
            return;
        }

        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPT_COUNT; attempt += 1) {
            try {
                int sentCount = mailSender.send(summary, recipients);
                recordDeliveryLog(
                        setting,
                        localDate,
                        TaskDueEmailAlertDeliveryStatus.SENT,
                        attempt,
                        sentCount,
                        null
                );
                setting.markSent(localDate);
                advanceNextSendAt(setting);
                return;
            } catch (RuntimeException ex) {
                lastException = ex;
                log.warn(
                        "Task due email alert send failed attempt={} userId={} localDate={}",
                        attempt,
                        setting.getUserId(),
                        localDate,
                        ex
                );
            }
        }

        recordDeliveryLog(
                setting,
                localDate,
                TaskDueEmailAlertDeliveryStatus.FAILED,
                MAX_ATTEMPT_COUNT,
                recipients.size(),
                errorMessage(lastException)
        );
        advanceNextSendAt(setting);
    }

    private LocalDate currentLocalDate(String timezone) {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(timezone));
    }

    private void recordDeliveryLog(
            TaskDueEmailAlertSetting setting,
            LocalDate localDate,
            TaskDueEmailAlertDeliveryStatus status,
            int attemptCount,
            int recipientCount,
            String errorMessage
    ) {
        deliveryLogRepository.save(TaskDueEmailAlertDeliveryLog.create(
                setting.getUserId(),
                localDate,
                status,
                attemptCount,
                recipientCount,
                errorMessage
        ));
    }

    private void advanceNextSendAt(TaskDueEmailAlertSetting setting) {
        setting.updateNextSendAt(nextSendAtCalculator.calculate(
                setting.isEnabled(),
                setting.getSendTime(),
                setting.getTimezone()
        ));
    }

    private String errorMessage(RuntimeException ex) {
        if (ex == null || ex.getMessage() == null) {
            return null;
        }
        String message = ex.getMessage();
        if (message.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
