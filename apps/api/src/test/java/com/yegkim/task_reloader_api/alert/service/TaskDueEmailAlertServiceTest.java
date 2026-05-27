package com.yegkim.task_reloader_api.alert.service;

import com.yegkim.task_reloader_api.alert.dto.AddTaskDueEmailAlertRecipientRequest;
import com.yegkim.task_reloader_api.alert.dto.TaskDueEmailAlertRecipientResponse;
import com.yegkim.task_reloader_api.alert.dto.TaskDueEmailAlertSettingsResponse;
import com.yegkim.task_reloader_api.alert.dto.UpdateTaskDueEmailAlertSettingsRequest;
import com.yegkim.task_reloader_api.alert.entity.TaskDueEmailAlertRecipient;
import com.yegkim.task_reloader_api.alert.entity.TaskDueEmailAlertSetting;
import com.yegkim.task_reloader_api.alert.exception.TaskDueEmailAlertException;
import com.yegkim.task_reloader_api.alert.repository.TaskDueEmailAlertDeliveryLogRepository;
import com.yegkim.task_reloader_api.alert.repository.TaskDueEmailAlertRecipientRepository;
import com.yegkim.task_reloader_api.alert.repository.TaskDueEmailAlertSettingRepository;
import com.yegkim.task_reloader_api.auth.entity.User;
import com.yegkim.task_reloader_api.auth.entity.UserRole;
import com.yegkim.task_reloader_api.auth.entity.UserStatus;
import com.yegkim.task_reloader_api.auth.repository.UserRepository;
import com.yegkim.task_reloader_api.auth.security.AuthenticatedUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TaskDueEmailAlertService 단위테스트")
class TaskDueEmailAlertServiceTest {

    private static final long USER_ID = 1L;

    @Mock
    private TaskDueEmailAlertSettingRepository settingRepository;

    @Mock
    private TaskDueEmailAlertRecipientRepository recipientRepository;

    @Mock
    private TaskDueEmailAlertDeliveryLogRepository deliveryLogRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthenticatedUserProvider authenticatedUserProvider;

    @Mock
    private TaskDueEmailAlertNextSendAtCalculator nextSendAtCalculator;

    @InjectMocks
    private TaskDueEmailAlertService taskDueEmailAlertService;

    private User user;

    @BeforeEach
    void setUp() {
        when(authenticatedUserProvider.currentUserId()).thenReturn(USER_ID);
        user = User.builder()
                .id(USER_ID)
                .email("Owner@Example.com")
                .passwordHash("hash")
                .role(UserRole.USER)
                .status(UserStatus.APPROVED)
                .build();
    }

    @Test
    @DisplayName("설정 조회 - 설정이 없으면 기본 설정을 만들고 로그인 이메일을 추천값으로 반환")
    void getSettingsCreatesDefaultSettingWithSuggestedEmail() {
        TaskDueEmailAlertSetting defaultSetting = TaskDueEmailAlertSetting.createDefault(USER_ID);
        when(settingRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(settingRepository.save(any(TaskDueEmailAlertSetting.class))).thenReturn(defaultSetting);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(deliveryLogRepository.findFirstByUserIdOrderByUpdatedAtDesc(USER_ID)).thenReturn(Optional.empty());

        TaskDueEmailAlertSettingsResponse response = taskDueEmailAlertService.getSettings();

        assertThat(response.enabled()).isFalse();
        assertThat(response.sendTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(response.timezone()).isEqualTo("Asia/Seoul");
        assertThat(response.suggestedEmail()).isEqualTo("Owner@Example.com");
        assertThat(response.maxRecipientCount()).isEqualTo(5);
        verify(settingRepository).save(any(TaskDueEmailAlertSetting.class));
    }

    @Test
    @DisplayName("설정 수정 - 활성화/시간/타임존을 갱신한다")
    void updateSettings() {
        TaskDueEmailAlertSetting setting = TaskDueEmailAlertSetting.createDefault(USER_ID);
        OffsetDateTime nextSendAt = OffsetDateTime.parse("2026-05-25T12:30:00Z");
        when(settingRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(setting));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(nextSendAtCalculator.calculate(true, LocalTime.of(8, 30), "America/New_York"))
                .thenReturn(nextSendAt);
        when(deliveryLogRepository.findFirstByUserIdOrderByUpdatedAtDesc(USER_ID)).thenReturn(Optional.empty());

        TaskDueEmailAlertSettingsResponse response = taskDueEmailAlertService.updateSettings(
                new UpdateTaskDueEmailAlertSettingsRequest(true, LocalTime.of(8, 30), "America/New_York")
        );

        assertThat(response.enabled()).isTrue();
        assertThat(response.sendTime()).isEqualTo(LocalTime.of(8, 30));
        assertThat(response.timezone()).isEqualTo("America/New_York");
        assertThat(setting.getNextSendAt()).isEqualTo(nextSendAt);
        verify(nextSendAtCalculator).calculate(true, LocalTime.of(8, 30), "America/New_York");
    }

    @Test
    @DisplayName("설정 수정 - 빈 타임존은 거부한다")
    void updateSettingsRejectsBlankTimezone() {
        TaskDueEmailAlertSetting setting = TaskDueEmailAlertSetting.createDefault(USER_ID);
        when(settingRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(setting));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> taskDueEmailAlertService.updateSettings(
                new UpdateTaskDueEmailAlertSettingsRequest(null, null, "   ")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("timezone은 비워둘 수 없습니다.");
    }

    @Test
    @DisplayName("설정 수정 - 지원하지 않는 타임존은 거부한다")
    void updateSettingsRejectsInvalidTimezone() {
        TaskDueEmailAlertSetting setting = TaskDueEmailAlertSetting.createDefault(USER_ID);
        when(settingRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(setting));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> taskDueEmailAlertService.updateSettings(
                new UpdateTaskDueEmailAlertSettingsRequest(null, null, "Not/A_Zone")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("지원하지 않는 timezone입니다.");
    }

    @Test
    @DisplayName("수신자 목록 조회 - 생성 순서대로 응답한다")
    void findRecipients() {
        OffsetDateTime now = OffsetDateTime.now();
        when(recipientRepository.findAllByUserIdOrderByCreatedAtAsc(USER_ID)).thenReturn(List.of(
                TaskDueEmailAlertRecipient.builder()
                        .id(10L)
                        .userId(USER_ID)
                        .email("owner@example.com")
                        .createdAt(now)
                        .build()
        ));

        List<TaskDueEmailAlertRecipientResponse> response = taskDueEmailAlertService.findRecipients();

        assertThat(response).hasSize(1);
        assertThat(response.get(0).id()).isEqualTo(10L);
        assertThat(response.get(0).email()).isEqualTo("owner@example.com");
    }

    @Test
    @DisplayName("수신자 추가 - 이메일을 소문자로 정규화해 저장한다")
    void addRecipientNormalizesEmail() {
        TaskDueEmailAlertSetting setting = TaskDueEmailAlertSetting.createDefault(USER_ID);
        when(settingRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(setting));
        when(recipientRepository.countByUserId(USER_ID)).thenReturn(0L);
        when(recipientRepository.existsByUserIdAndEmailIgnoreCase(USER_ID, "owner@example.com")).thenReturn(false);
        when(recipientRepository.save(any(TaskDueEmailAlertRecipient.class))).thenAnswer(invocation -> {
            TaskDueEmailAlertRecipient recipient = invocation.getArgument(0);
            return TaskDueEmailAlertRecipient.builder()
                    .id(10L)
                    .userId(recipient.getUserId())
                    .email(recipient.getEmail())
                    .createdAt(OffsetDateTime.now())
                    .build();
        });

        TaskDueEmailAlertRecipientResponse response = taskDueEmailAlertService.addRecipient(
                new AddTaskDueEmailAlertRecipientRequest("  OWNER@Example.COM  ")
        );

        ArgumentCaptor<TaskDueEmailAlertRecipient> captor =
                ArgumentCaptor.forClass(TaskDueEmailAlertRecipient.class);
        verify(recipientRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("owner@example.com");
        assertThat(response.email()).isEqualTo("owner@example.com");
    }

    @Test
    @DisplayName("수신자 추가 - 최대 5개를 넘으면 거부한다")
    void addRecipientRejectsLimitExceeded() {
        TaskDueEmailAlertSetting setting = TaskDueEmailAlertSetting.createDefault(USER_ID);
        when(settingRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(setting));
        when(recipientRepository.countByUserId(USER_ID)).thenReturn(5L);

        assertThatThrownBy(() -> taskDueEmailAlertService.addRecipient(
                new AddTaskDueEmailAlertRecipientRequest("owner@example.com")
        )).isInstanceOf(TaskDueEmailAlertException.class)
                .hasMessage("수신 이메일은 최대 5개까지 등록할 수 있습니다.");

        verify(recipientRepository, never()).save(any());
    }

    @Test
    @DisplayName("수신자 추가 - 이미 등록된 이메일이면 거부한다")
    void addRecipientRejectsDuplicateEmail() {
        TaskDueEmailAlertSetting setting = TaskDueEmailAlertSetting.createDefault(USER_ID);
        when(settingRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(setting));
        when(recipientRepository.countByUserId(USER_ID)).thenReturn(1L);
        when(recipientRepository.existsByUserIdAndEmailIgnoreCase(USER_ID, "owner@example.com")).thenReturn(true);

        assertThatThrownBy(() -> taskDueEmailAlertService.addRecipient(
                new AddTaskDueEmailAlertRecipientRequest("OWNER@example.com")
        )).isInstanceOf(TaskDueEmailAlertException.class)
                .hasMessage("이미 등록된 수신 이메일입니다.");

        verify(recipientRepository, never()).save(any());
    }

    @Test
    @DisplayName("수신자 삭제 - 사용자 소유 수신자만 삭제한다")
    void deleteRecipient() {
        when(recipientRepository.deleteByIdAndUserId(10L, USER_ID)).thenReturn(1L);

        taskDueEmailAlertService.deleteRecipient(10L);

        verify(recipientRepository).deleteByIdAndUserId(10L, USER_ID);
    }

    @Test
    @DisplayName("수신자 삭제 - 삭제 대상이 없으면 404 예외")
    void deleteRecipientNotFound() {
        when(recipientRepository.deleteByIdAndUserId(10L, USER_ID)).thenReturn(0L);

        assertThatThrownBy(() -> taskDueEmailAlertService.deleteRecipient(10L))
                .isInstanceOf(TaskDueEmailAlertException.class)
                .hasMessage("수신 이메일을 찾을 수 없습니다.");
    }
}
