package com.yegkim.task_reloader_api.alert.repository;

import com.yegkim.task_reloader_api.alert.entity.TaskDueEmailAlertDeliveryLog;
import com.yegkim.task_reloader_api.alert.entity.TaskDueEmailAlertDeliveryStatus;
import com.yegkim.task_reloader_api.alert.entity.TaskDueEmailAlertRecipient;
import com.yegkim.task_reloader_api.alert.entity.TaskDueEmailAlertSetting;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Testcontainers
@DisplayName("TaskDueEmailAlertRepository JPA 테스트")
class TaskDueEmailAlertRepositoryTest {

    private static final long ADMIN_USER_ID = 1L;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.placeholders.auth_admin_email", () -> "admin@task-reloader.local");
        registry.add("spring.flyway.placeholders.auth_admin_password_hash",
                () -> "$2a$12$yA0NQILk2h0m9Pk5IXf4Y.j6pESf9bnC8sY8VAsxN1uQf9P4j2Q0m");
    }

    @Autowired
    private TaskDueEmailAlertSettingRepository settingRepository;

    @Autowired
    private TaskDueEmailAlertRecipientRepository recipientRepository;

    @Autowired
    private TaskDueEmailAlertDeliveryLogRepository deliveryLogRepository;

    @Test
    @DisplayName("마이그레이션은 기존 사용자에게 기본 알림 설정을 생성한다")
    void migrationBackfillsDefaultSettingForExistingUser() {
        TaskDueEmailAlertSetting setting = settingRepository.findById(ADMIN_USER_ID).orElseThrow();

        assertThat(setting.isEnabled()).isFalse();
        assertThat(setting.getSendTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(setting.getTimezone()).isEqualTo("Asia/Seoul");
        assertThat(setting.getLastSentLocalDate()).isNull();
        assertThat(setting.getNextSendAt()).isNull();
    }

    @Test
    @DisplayName("활성화된 알림 설정만 조회한다")
    void findAllByEnabledTrue() {
        TaskDueEmailAlertSetting setting = settingRepository.findById(ADMIN_USER_ID).orElseThrow();
        setting.update(true, LocalTime.of(8, 30), "Asia/Seoul");
        settingRepository.saveAndFlush(setting);

        List<TaskDueEmailAlertSetting> result = settingRepository.findAllByEnabledTrue();

        assertThat(result)
                .extracting(TaskDueEmailAlertSetting::getUserId)
                .containsExactly(ADMIN_USER_ID);
    }

    @Test
    @DisplayName("발송 예정 시각이 지난 활성 알림 설정만 조회한다")
    void findDueSettingsForUpdate() {
        TaskDueEmailAlertSetting setting = settingRepository.findById(ADMIN_USER_ID).orElseThrow();
        setting.update(true, LocalTime.of(8, 30), "Asia/Seoul");
        setting.updateNextSendAt(OffsetDateTime.parse("2026-05-25T00:00:00Z"));
        settingRepository.saveAndFlush(setting);

        List<TaskDueEmailAlertSetting> result =
                settingRepository.findDueSettingsForUpdate(OffsetDateTime.parse("2026-05-25T00:01:00Z"), 10);

        assertThat(result)
                .extracting(TaskDueEmailAlertSetting::getUserId)
                .containsExactly(ADMIN_USER_ID);
    }

    @Test
    @DisplayName("비활성 알림이거나 발송 예정 시각이 미래면 due 조회에서 제외한다")
    void findDueSettingsForUpdateExcludesDisabledAndFutureSettings() {
        TaskDueEmailAlertSetting setting = settingRepository.findById(ADMIN_USER_ID).orElseThrow();
        setting.update(false, LocalTime.of(8, 30), "Asia/Seoul");
        setting.updateNextSendAt(OffsetDateTime.parse("2026-05-25T00:00:00Z"));
        settingRepository.saveAndFlush(setting);

        assertThat(settingRepository.findDueSettingsForUpdate(
                OffsetDateTime.parse("2026-05-25T00:01:00Z"),
                10
        )).isEmpty();

        setting.update(true, LocalTime.of(8, 30), "Asia/Seoul");
        setting.updateNextSendAt(OffsetDateTime.parse("2026-05-25T00:02:00Z"));
        settingRepository.saveAndFlush(setting);

        assertThat(settingRepository.findDueSettingsForUpdate(
                OffsetDateTime.parse("2026-05-25T00:01:00Z"),
                10
        )).isEmpty();
    }

    @Test
    @DisplayName("수신 이메일은 생성 순서로 조회하고 대소문자 무시 중복을 확인한다")
    void findRecipientsByUserAndDetectDuplicateEmailIgnoringCase() {
        TaskDueEmailAlertRecipient first = recipientRepository.save(
                TaskDueEmailAlertRecipient.create(ADMIN_USER_ID, "owner@example.com")
        );
        TaskDueEmailAlertRecipient second = recipientRepository.save(
                TaskDueEmailAlertRecipient.create(ADMIN_USER_ID, "backup@example.com")
        );
        recipientRepository.flush();

        List<TaskDueEmailAlertRecipient> recipients =
                recipientRepository.findAllByUserIdOrderByCreatedAtAsc(ADMIN_USER_ID);

        assertThat(recipients).containsExactly(first, second);
        assertThat(recipientRepository.countByUserId(ADMIN_USER_ID)).isEqualTo(2);
        assertThat(recipientRepository.existsByUserIdAndEmailIgnoreCase(ADMIN_USER_ID, "OWNER@example.com")).isTrue();
    }

    @Test
    @DisplayName("동일 사용자에게 같은 이메일은 대소문자가 달라도 중복 저장할 수 없다")
    void duplicateRecipientEmailForSameUserIsRejectedIgnoringCase() {
        recipientRepository.saveAndFlush(TaskDueEmailAlertRecipient.create(ADMIN_USER_ID, "owner@example.com"));

        assertThatThrownBy(() -> recipientRepository.saveAndFlush(
                TaskDueEmailAlertRecipient.create(ADMIN_USER_ID, "OWNER@example.com")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("발송 로그는 사용자와 로컬 날짜 기준으로 조회한다")
    void findDeliveryLogByUserIdAndLocalDate() {
        LocalDate localDate = LocalDate.of(2026, 5, 21);
        TaskDueEmailAlertDeliveryLog saved = deliveryLogRepository.saveAndFlush(
                TaskDueEmailAlertDeliveryLog.create(
                        ADMIN_USER_ID,
                        localDate,
                        TaskDueEmailAlertDeliveryStatus.SENT,
                        1,
                        2,
                        null
                )
        );

        assertThat(deliveryLogRepository.existsByUserIdAndLocalDate(ADMIN_USER_ID, localDate)).isTrue();
        assertThat(deliveryLogRepository.findByUserIdAndLocalDate(ADMIN_USER_ID, localDate)).contains(saved);
        assertThat(deliveryLogRepository.findByUserIdAndLocalDateForUpdate(ADMIN_USER_ID, localDate)).contains(saved);
    }

    @Test
    @DisplayName("같은 사용자와 같은 로컬 날짜의 발송 로그는 중복 저장할 수 없다")
    void duplicateDeliveryLogForSameUserAndLocalDateIsRejected() {
        LocalDate localDate = LocalDate.of(2026, 5, 21);
        deliveryLogRepository.saveAndFlush(TaskDueEmailAlertDeliveryLog.create(
                ADMIN_USER_ID,
                localDate,
                TaskDueEmailAlertDeliveryStatus.FAILED,
                1,
                1,
                "temporary failure"
        ));

        assertThatThrownBy(() -> deliveryLogRepository.saveAndFlush(TaskDueEmailAlertDeliveryLog.create(
                ADMIN_USER_ID,
                localDate,
                TaskDueEmailAlertDeliveryStatus.SENT,
                2,
                1,
                null
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }
}
