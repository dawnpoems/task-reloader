package com.yegkim.task_reloader_api.alert.service;

import com.yegkim.task_reloader_api.alert.dto.TaskDueEmailAlertSummary;
import com.yegkim.task_reloader_api.alert.dto.TaskDueEmailAlertTestSendResponse;
import com.yegkim.task_reloader_api.alert.entity.TaskDueEmailAlertRecipient;
import com.yegkim.task_reloader_api.alert.entity.TaskDueEmailAlertSetting;
import com.yegkim.task_reloader_api.alert.mail.TaskDueEmailAlertMailSender;
import com.yegkim.task_reloader_api.alert.repository.TaskDueEmailAlertRecipientRepository;
import com.yegkim.task_reloader_api.alert.repository.TaskDueEmailAlertSettingRepository;
import com.yegkim.task_reloader_api.auth.security.AuthenticatedUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Profile("local")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskDueEmailAlertTestSendService {

    private static final String NO_RECIPIENTS_REASON = "등록된 수신 이메일이 없습니다.";
    private static final String EMPTY_SUMMARY_REASON = "오늘 마감 또는 지난 작업이 없습니다.";

    private final AuthenticatedUserProvider authenticatedUserProvider;
    private final TaskDueEmailAlertSettingRepository settingRepository;
    private final TaskDueEmailAlertRecipientRepository recipientRepository;
    private final TaskDueEmailAlertAggregationService aggregationService;
    private final TaskDueEmailAlertMailSender mailSender;

    public TaskDueEmailAlertTestSendResponse sendNow() {
        Long userId = authenticatedUserProvider.currentUserId();
        TaskDueEmailAlertSetting setting = settingRepository.findById(userId)
                .orElseGet(() -> TaskDueEmailAlertSetting.createDefault(userId));
        List<String> recipients = recipientRepository.findAllByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(TaskDueEmailAlertRecipient::getEmail)
                .toList();
        TaskDueEmailAlertSummary summary = aggregationService.aggregate(userId, setting.getTimezone());

        if (recipients.isEmpty()) {
            return response(summary, recipients.size(), 0, NO_RECIPIENTS_REASON);
        }
        if (summary.isEmpty()) {
            return response(summary, recipients.size(), 0, EMPTY_SUMMARY_REASON);
        }

        int sentCount = mailSender.send(summary, recipients);
        return response(summary, recipients.size(), sentCount, null);
    }

    private TaskDueEmailAlertTestSendResponse response(
            TaskDueEmailAlertSummary summary,
            int recipientCount,
            int sentCount,
            String skippedReason
    ) {
        return new TaskDueEmailAlertTestSendResponse(
                sentCount,
                recipientCount,
                summary.dueTodayCount(),
                summary.overdueCount(),
                summary.totalCount(),
                summary.localDate(),
                summary.timezone(),
                skippedReason
        );
    }
}
