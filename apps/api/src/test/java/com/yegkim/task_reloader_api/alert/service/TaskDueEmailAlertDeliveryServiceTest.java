package com.yegkim.task_reloader_api.alert.service;

import com.yegkim.task_reloader_api.alert.dto.TaskDueEmailAlertSummary;
import com.yegkim.task_reloader_api.alert.dto.TaskDueEmailAlertTaskItem;
import com.yegkim.task_reloader_api.alert.entity.TaskDueEmailAlertDeliveryLog;
import com.yegkim.task_reloader_api.alert.entity.TaskDueEmailAlertDeliveryStatus;
import com.yegkim.task_reloader_api.alert.entity.TaskDueEmailAlertRecipient;
import com.yegkim.task_reloader_api.alert.entity.TaskDueEmailAlertSetting;
import com.yegkim.task_reloader_api.alert.mail.TaskDueEmailAlertMailSender;
import com.yegkim.task_reloader_api.alert.repository.TaskDueEmailAlertDeliveryLogRepository;
import com.yegkim.task_reloader_api.alert.repository.TaskDueEmailAlertRecipientRepository;
import com.yegkim.task_reloader_api.alert.repository.TaskDueEmailAlertSettingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("TaskDueEmailAlertDeliveryService 단위테스트")
class TaskDueEmailAlertDeliveryServiceTest {

    private static final long USER_ID = 1L;
    private static final LocalDate LOCAL_DATE = LocalDate.of(2026, 5, 25);
    private static final OffsetDateTime NOW_UTC = OffsetDateTime.parse("2026-05-25T00:00:00Z");
    private static final OffsetDateTime NEXT_SEND_AT = OffsetDateTime.parse("2026-05-26T00:00:00Z");

    private final TaskDueEmailAlertSettingRepository settingRepository =
            mock(TaskDueEmailAlertSettingRepository.class);
    private final TaskDueEmailAlertRecipientRepository recipientRepository =
            mock(TaskDueEmailAlertRecipientRepository.class);
    private final TaskDueEmailAlertDeliveryLogRepository deliveryLogRepository =
            mock(TaskDueEmailAlertDeliveryLogRepository.class);
    private final TaskDueEmailAlertAggregationService aggregationService =
            mock(TaskDueEmailAlertAggregationService.class);
    private final TaskDueEmailAlertMailSender mailSender =
            mock(TaskDueEmailAlertMailSender.class);
    private final TaskDueEmailAlertNextSendAtCalculator nextSendAtCalculator =
            mock(TaskDueEmailAlertNextSendAtCalculator.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-25T00:00:00Z"), ZoneOffset.UTC);
    private final TaskDueEmailAlertDeliveryService deliveryService = new TaskDueEmailAlertDeliveryService(
            settingRepository,
            recipientRepository,
            deliveryLogRepository,
            aggregationService,
            mailSender,
            nextSendAtCalculator,
            clock
    );

    @Test
    @DisplayName("발송 대상 설정이 없으면 0건을 반환한다")
    void deliverDueSettingsWithoutDueSettings() {
        when(settingRepository.findDueSettingsForUpdate(NOW_UTC, 100)).thenReturn(List.of());

        int processedCount = deliveryService.deliverDueSettings(100);

        assertThat(processedCount).isZero();
    }

    @Test
    @DisplayName("이미 오늘 발송 로그가 있으면 메일을 보내지 않고 다음 발송 시각만 갱신한다")
    void skipWhenDeliveryLogAlreadyExists() {
        TaskDueEmailAlertSetting setting = setting();
        TaskDueEmailAlertDeliveryLog existingLog = TaskDueEmailAlertDeliveryLog.create(
                USER_ID,
                LOCAL_DATE,
                TaskDueEmailAlertDeliveryStatus.SENT,
                1,
                1,
                null
        );
        givenDueSetting(setting);
        when(deliveryLogRepository.findByUserIdAndLocalDateForUpdate(USER_ID, LOCAL_DATE))
                .thenReturn(Optional.of(existingLog));
        givenNextSendAt();

        int processedCount = deliveryService.deliverDueSettings(100);

        assertThat(processedCount).isEqualTo(1);
        assertThat(setting.getNextSendAt()).isEqualTo(NEXT_SEND_AT);
        verify(recipientRepository, never()).findAllByUserIdOrderByCreatedAtAsc(USER_ID);
        verify(aggregationService, never()).aggregate(eq(USER_ID), any());
        verify(mailSender, never()).send(any(), any());
        verify(deliveryLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("수신자가 없으면 SKIPPED 로그를 저장하고 메일을 보내지 않는다")
    void skipWithoutRecipients() {
        TaskDueEmailAlertSetting setting = setting();
        givenDueSettingWithoutExistingLog(setting);
        when(recipientRepository.findAllByUserIdOrderByCreatedAtAsc(USER_ID)).thenReturn(List.of());
        when(aggregationService.aggregate(USER_ID, "Asia/Seoul")).thenReturn(nonEmptySummary());
        givenNextSendAt();

        deliveryService.deliverDueSettings(100);

        TaskDueEmailAlertDeliveryLog savedLog = capturedSavedLog();
        assertThat(savedLog.getStatus()).isEqualTo(TaskDueEmailAlertDeliveryStatus.SKIPPED);
        assertThat(savedLog.getRecipientCount()).isZero();
        assertThat(savedLog.getAttemptCount()).isEqualTo(1);
        assertThat(savedLog.getErrorMessage()).isEqualTo("등록된 수신 이메일이 없습니다.");
        assertThat(setting.getNextSendAt()).isEqualTo(NEXT_SEND_AT);
        verify(mailSender, never()).send(any(), any());
    }

    @Test
    @DisplayName("발송 대상 작업이 0건이면 SKIPPED 로그를 저장하고 메일을 보내지 않는다")
    void skipEmptySummary() {
        TaskDueEmailAlertSetting setting = setting();
        givenDueSettingWithoutExistingLog(setting);
        when(recipientRepository.findAllByUserIdOrderByCreatedAtAsc(USER_ID))
                .thenReturn(List.of(recipient("owner@example.com")));
        when(aggregationService.aggregate(USER_ID, "Asia/Seoul")).thenReturn(emptySummary());
        givenNextSendAt();

        deliveryService.deliverDueSettings(100);

        TaskDueEmailAlertDeliveryLog savedLog = capturedSavedLog();
        assertThat(savedLog.getStatus()).isEqualTo(TaskDueEmailAlertDeliveryStatus.SKIPPED);
        assertThat(savedLog.getRecipientCount()).isEqualTo(1);
        assertThat(savedLog.getAttemptCount()).isEqualTo(1);
        assertThat(savedLog.getErrorMessage()).isEqualTo("오늘 마감 또는 지난 작업이 없습니다.");
        assertThat(setting.getNextSendAt()).isEqualTo(NEXT_SEND_AT);
        verify(mailSender, never()).send(any(), any());
    }

    @Test
    @DisplayName("메일 발송에 성공하면 SENT 로그와 마지막 발송일을 저장한다")
    void sendSuccess() {
        TaskDueEmailAlertSetting setting = setting();
        TaskDueEmailAlertSummary summary = nonEmptySummary();
        givenDueSettingWithoutExistingLog(setting);
        when(recipientRepository.findAllByUserIdOrderByCreatedAtAsc(USER_ID))
                .thenReturn(List.of(recipient("owner@example.com"), recipient("team@example.com")));
        when(aggregationService.aggregate(USER_ID, "Asia/Seoul")).thenReturn(summary);
        when(mailSender.send(summary, List.of("owner@example.com", "team@example.com"))).thenReturn(2);
        givenNextSendAt();

        deliveryService.deliverDueSettings(100);

        TaskDueEmailAlertDeliveryLog savedLog = capturedSavedLog();
        assertThat(savedLog.getStatus()).isEqualTo(TaskDueEmailAlertDeliveryStatus.SENT);
        assertThat(savedLog.getRecipientCount()).isEqualTo(2);
        assertThat(savedLog.getAttemptCount()).isEqualTo(1);
        assertThat(savedLog.getErrorMessage()).isNull();
        assertThat(setting.getLastSentLocalDate()).isEqualTo(LOCAL_DATE);
        assertThat(setting.getNextSendAt()).isEqualTo(NEXT_SEND_AT);
    }

    @Test
    @DisplayName("메일 발송 실패는 최대 2회 시도 후 FAILED 로그를 저장한다")
    void sendFailureRetriesTwice() {
        TaskDueEmailAlertSetting setting = setting();
        TaskDueEmailAlertSummary summary = nonEmptySummary();
        List<String> recipients = List.of("owner@example.com");
        givenDueSettingWithoutExistingLog(setting);
        when(recipientRepository.findAllByUserIdOrderByCreatedAtAsc(USER_ID))
                .thenReturn(List.of(recipient("owner@example.com")));
        when(aggregationService.aggregate(USER_ID, "Asia/Seoul")).thenReturn(summary);
        when(mailSender.send(summary, recipients)).thenThrow(new RuntimeException("smtp failed"));
        givenNextSendAt();

        deliveryService.deliverDueSettings(100);

        TaskDueEmailAlertDeliveryLog savedLog = capturedSavedLog();
        assertThat(savedLog.getStatus()).isEqualTo(TaskDueEmailAlertDeliveryStatus.FAILED);
        assertThat(savedLog.getRecipientCount()).isEqualTo(1);
        assertThat(savedLog.getAttemptCount()).isEqualTo(2);
        assertThat(savedLog.getErrorMessage()).isEqualTo("smtp failed");
        assertThat(setting.getLastSentLocalDate()).isNull();
        assertThat(setting.getNextSendAt()).isEqualTo(NEXT_SEND_AT);
        verify(mailSender, times(2)).send(summary, recipients);
    }

    private void givenDueSetting(TaskDueEmailAlertSetting setting) {
        when(settingRepository.findDueSettingsForUpdate(NOW_UTC, 100)).thenReturn(List.of(setting));
    }

    private void givenDueSettingWithoutExistingLog(TaskDueEmailAlertSetting setting) {
        givenDueSetting(setting);
        when(deliveryLogRepository.findByUserIdAndLocalDateForUpdate(USER_ID, LOCAL_DATE))
                .thenReturn(Optional.empty());
    }

    private void givenNextSendAt() {
        when(nextSendAtCalculator.calculate(true, LocalTime.of(9, 0), "Asia/Seoul"))
                .thenReturn(NEXT_SEND_AT);
    }

    private TaskDueEmailAlertDeliveryLog capturedSavedLog() {
        ArgumentCaptor<TaskDueEmailAlertDeliveryLog> captor =
                ArgumentCaptor.forClass(TaskDueEmailAlertDeliveryLog.class);
        verify(deliveryLogRepository).save(captor.capture());
        return captor.getValue();
    }

    private TaskDueEmailAlertSetting setting() {
        return TaskDueEmailAlertSetting.builder()
                .userId(USER_ID)
                .enabled(true)
                .sendTime(LocalTime.of(9, 0))
                .timezone("Asia/Seoul")
                .nextSendAt(NOW_UTC)
                .build();
    }

    private TaskDueEmailAlertRecipient recipient(String email) {
        return TaskDueEmailAlertRecipient.builder()
                .userId(USER_ID)
                .email(email)
                .build();
    }

    private TaskDueEmailAlertSummary emptySummary() {
        return new TaskDueEmailAlertSummary(
                USER_ID,
                LOCAL_DATE,
                "Asia/Seoul",
                List.of(),
                List.of()
        );
    }

    private TaskDueEmailAlertSummary nonEmptySummary() {
        return new TaskDueEmailAlertSummary(
                USER_ID,
                LOCAL_DATE,
                "Asia/Seoul",
                List.of(new TaskDueEmailAlertTaskItem(
                        10L,
                        "오늘 작업",
                        NOW_UTC,
                        LOCAL_DATE
                )),
                List.of()
        );
    }
}
