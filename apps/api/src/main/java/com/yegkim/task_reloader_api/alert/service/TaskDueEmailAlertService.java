package com.yegkim.task_reloader_api.alert.service;

import com.yegkim.task_reloader_api.alert.dto.AddTaskDueEmailAlertRecipientRequest;
import com.yegkim.task_reloader_api.alert.dto.TaskDueEmailAlertLastDeliveryResponse;
import com.yegkim.task_reloader_api.alert.dto.TaskDueEmailAlertRecipientResponse;
import com.yegkim.task_reloader_api.alert.dto.TaskDueEmailAlertSettingsResponse;
import com.yegkim.task_reloader_api.alert.dto.UpdateTaskDueEmailAlertSettingsRequest;
import com.yegkim.task_reloader_api.alert.entity.TaskDueEmailAlertDeliveryLog;
import com.yegkim.task_reloader_api.alert.entity.TaskDueEmailAlertRecipient;
import com.yegkim.task_reloader_api.alert.entity.TaskDueEmailAlertSetting;
import com.yegkim.task_reloader_api.alert.exception.TaskDueEmailAlertException;
import com.yegkim.task_reloader_api.alert.repository.TaskDueEmailAlertDeliveryLogRepository;
import com.yegkim.task_reloader_api.alert.repository.TaskDueEmailAlertRecipientRepository;
import com.yegkim.task_reloader_api.alert.repository.TaskDueEmailAlertSettingRepository;
import com.yegkim.task_reloader_api.auth.entity.User;
import com.yegkim.task_reloader_api.auth.exception.AuthException;
import com.yegkim.task_reloader_api.auth.repository.UserRepository;
import com.yegkim.task_reloader_api.auth.security.AuthenticatedUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskDueEmailAlertService {

    public static final int MAX_RECIPIENT_COUNT = 5;

    private final TaskDueEmailAlertSettingRepository settingRepository;
    private final TaskDueEmailAlertRecipientRepository recipientRepository;
    private final TaskDueEmailAlertDeliveryLogRepository deliveryLogRepository;
    private final UserRepository userRepository;
    private final AuthenticatedUserProvider authenticatedUserProvider;
    private final TaskDueEmailAlertNextSendAtCalculator nextSendAtCalculator;

    @Transactional
    public TaskDueEmailAlertSettingsResponse getSettings() {
        Long userId = authenticatedUserProvider.currentUserId();
        TaskDueEmailAlertSetting setting = getOrCreateSetting(userId);
        User user = findCurrentUser(userId);

        return toSettingsResponse(setting, user.getEmail());
    }

    @Transactional
    public TaskDueEmailAlertSettingsResponse updateSettings(UpdateTaskDueEmailAlertSettingsRequest request) {
        Long userId = authenticatedUserProvider.currentUserId();
        TaskDueEmailAlertSetting setting = getOrCreateSettingForUpdate(userId);
        User user = findCurrentUser(userId);

        setting.update(
                request.enabled(),
                request.sendTime(),
                normalizeTimezone(request.timezone())
        );
        setting.updateNextSendAt(nextSendAtCalculator.calculate(
                setting.isEnabled(),
                setting.getSendTime(),
                setting.getTimezone()
        ));

        return toSettingsResponse(setting, user.getEmail());
    }

    public List<TaskDueEmailAlertRecipientResponse> findRecipients() {
        Long userId = authenticatedUserProvider.currentUserId();
        return recipientRepository.findAllByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(this::toRecipientResponse)
                .toList();
    }

    @Transactional
    public TaskDueEmailAlertRecipientResponse addRecipient(AddTaskDueEmailAlertRecipientRequest request) {
        Long userId = authenticatedUserProvider.currentUserId();
        getOrCreateSettingForUpdate(userId);

        if (recipientRepository.countByUserId(userId) >= MAX_RECIPIENT_COUNT) {
            throw new TaskDueEmailAlertException(
                    HttpStatus.CONFLICT,
                    "TASK_DUE_EMAIL_ALERT_RECIPIENT_LIMIT_EXCEEDED",
                    "수신 이메일은 최대 5개까지 등록할 수 있습니다."
            );
        }

        String email = normalizeEmail(request.email());
        if (recipientRepository.existsByUserIdAndEmailIgnoreCase(userId, email)) {
            throw new TaskDueEmailAlertException(
                    HttpStatus.CONFLICT,
                    "TASK_DUE_EMAIL_ALERT_RECIPIENT_DUPLICATE",
                    "이미 등록된 수신 이메일입니다."
            );
        }

        TaskDueEmailAlertRecipient recipient = recipientRepository.save(
                TaskDueEmailAlertRecipient.create(userId, email)
        );
        return toRecipientResponse(recipient);
    }

    @Transactional
    public void deleteRecipient(Long recipientId) {
        Long userId = authenticatedUserProvider.currentUserId();
        long deletedCount = recipientRepository.deleteByIdAndUserId(recipientId, userId);
        if (deletedCount == 0) {
            throw new TaskDueEmailAlertException(
                    HttpStatus.NOT_FOUND,
                    "TASK_DUE_EMAIL_ALERT_RECIPIENT_NOT_FOUND",
                    "수신 이메일을 찾을 수 없습니다."
            );
        }
    }

    private TaskDueEmailAlertSetting getOrCreateSetting(Long userId) {
        return settingRepository.findById(userId)
                .orElseGet(() -> settingRepository.save(TaskDueEmailAlertSetting.createDefault(userId)));
    }

    private TaskDueEmailAlertSetting getOrCreateSettingForUpdate(Long userId) {
        return settingRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> settingRepository.saveAndFlush(TaskDueEmailAlertSetting.createDefault(userId)));
    }

    private User findCurrentUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(
                        HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND",
                        "사용자 정보를 찾을 수 없습니다."
                ));
    }

    private TaskDueEmailAlertSettingsResponse toSettingsResponse(
            TaskDueEmailAlertSetting setting,
            String suggestedEmail
    ) {
        return new TaskDueEmailAlertSettingsResponse(
                setting.isEnabled(),
                setting.getSendTime(),
                setting.getTimezone(),
                setting.getLastSentLocalDate(),
                suggestedEmail,
                MAX_RECIPIENT_COUNT,
                deliveryLogRepository.findFirstByUserIdOrderByUpdatedAtDesc(setting.getUserId())
                        .map(this::toLastDeliveryResponse)
                        .orElse(null)
        );
    }

    private TaskDueEmailAlertLastDeliveryResponse toLastDeliveryResponse(TaskDueEmailAlertDeliveryLog deliveryLog) {
        return new TaskDueEmailAlertLastDeliveryResponse(
                deliveryLog.getStatus(),
                deliveryLog.getLocalDate(),
                deliveryLog.getAttemptCount(),
                deliveryLog.getRecipientCount(),
                deliveryLog.getErrorMessage(),
                deliveryLog.getCreatedAt(),
                deliveryLog.getUpdatedAt()
        );
    }

    private TaskDueEmailAlertRecipientResponse toRecipientResponse(TaskDueEmailAlertRecipient recipient) {
        return new TaskDueEmailAlertRecipientResponse(
                recipient.getId(),
                recipient.getEmail(),
                recipient.getCreatedAt()
        );
    }

    private String normalizeTimezone(String timezone) {
        if (timezone == null) {
            return null;
        }

        String normalized = timezone.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("timezone은 비워둘 수 없습니다.");
        }

        try {
            ZoneId.of(normalized);
            return normalized;
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("지원하지 않는 timezone입니다.");
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
