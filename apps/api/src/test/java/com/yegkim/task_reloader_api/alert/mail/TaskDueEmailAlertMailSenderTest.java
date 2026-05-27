package com.yegkim.task_reloader_api.alert.mail;

import com.yegkim.task_reloader_api.alert.config.TaskDueEmailAlertMailConfig;
import com.yegkim.task_reloader_api.alert.dto.TaskDueEmailAlertMailContent;
import com.yegkim.task_reloader_api.alert.dto.TaskDueEmailAlertSummary;
import com.yegkim.task_reloader_api.alert.dto.TaskDueEmailAlertTaskItem;
import com.yegkim.task_reloader_api.auth.repository.UserRepository;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("TaskDueEmailAlertMailSender 단위테스트")
class TaskDueEmailAlertMailSenderTest {

    private final JavaMailSender javaMailSender = mock(JavaMailSender.class);
    private final TaskDueEmailAlertMailTemplateRenderer templateRenderer =
            mock(TaskDueEmailAlertMailTemplateRenderer.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final JavaMailSenderImpl mimeMessageFactory = new JavaMailSenderImpl();
    private final TaskDueEmailAlertMailSender sender = new TaskDueEmailAlertMailSender(
            javaMailSender,
            templateRenderer,
            new TaskDueEmailAlertMailConfig("no-reply@task-reloader.local", "https://task.example.com"),
            userRepository
    );

    @Test
    @DisplayName("발송 대상 작업이 0건이면 템플릿 렌더링과 SMTP 발송을 하지 않는다")
    void skipEmptySummary() {
        int sentCount = sender.send(summary(List.of(), List.of()), List.of("owner@example.com"));

        assertThat(sentCount).isZero();
        verifyNoInteractions(templateRenderer, javaMailSender);
    }

    @Test
    @DisplayName("수신자 목록이 없으면 템플릿 렌더링과 SMTP 발송을 하지 않는다")
    void skipEmptyRecipients() {
        int sentCount = sender.send(nonEmptySummary(), List.of());

        assertThat(sentCount).isZero();
        verifyNoInteractions(templateRenderer, javaMailSender);
    }

    @Test
    @DisplayName("공백 수신자는 건너뛰고 유효한 수신자에게만 개별 발송한다")
    void sendOnlyValidRecipients() throws Exception {
        TaskDueEmailAlertSummary summary = nonEmptySummary();
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        when(templateRenderer.render(summary, null)).thenReturn(content());
        when(javaMailSender.createMimeMessage()).thenAnswer(invocation -> mimeMessageFactory.createMimeMessage());

        int sentCount = sender.send(summary, Arrays.asList("owner@example.com", "   ", null, "team@example.com"));

        assertThat(sentCount).isEqualTo(2);
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender, times(2)).send(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(message -> message.getRecipients(Message.RecipientType.TO)[0].toString())
                .containsExactly("owner@example.com", "team@example.com");
        assertThat(captor.getAllValues().get(0).getFrom()[0].toString())
                .isEqualTo("no-reply@task-reloader.local");
        assertThat(captor.getAllValues().get(0).getSubject())
                .isEqualTo("[Task Reloader] 오늘 마감 1개, 지난 작업 0개");
    }

    @Test
    @DisplayName("SMTP 발송 실패는 작업 마감 이메일 알림 예외로 감싼다")
    void wrapMailSendFailure() {
        TaskDueEmailAlertSummary summary = nonEmptySummary();
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        when(templateRenderer.render(summary, null)).thenReturn(content());
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessageFactory.createMimeMessage());
        doThrow(new MailSendException("smtp failed")).when(javaMailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> sender.send(summary, List.of("owner@example.com")))
                .isInstanceOf(TaskDueEmailAlertMailException.class)
                .hasMessage("작업 마감 이메일 알림 발송에 실패했습니다.")
                .hasCauseInstanceOf(MailSendException.class);

        verify(javaMailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("수신자가 null이면 템플릿 렌더링과 SMTP 발송을 하지 않는다")
    void skipNullRecipients() {
        int sentCount = sender.send(nonEmptySummary(), null);

        assertThat(sentCount).isZero();
        verifyNoInteractions(templateRenderer, javaMailSender);
    }

    @Test
    @DisplayName("수신자가 모두 공백이면 SMTP 발송하지 않는다")
    void skipAllBlankRecipients() {
        TaskDueEmailAlertSummary summary = nonEmptySummary();
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        int sentCount = sender.send(summary, List.of(" ", "\t"));

        assertThat(sentCount).isZero();
        verify(javaMailSender, never()).createMimeMessage();
        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }

    private TaskDueEmailAlertMailContent content() {
        return new TaskDueEmailAlertMailContent(
                "[Task Reloader] 오늘 마감 1개, 지난 작업 0개",
                "<p>html</p>",
                "text"
        );
    }

    private TaskDueEmailAlertSummary nonEmptySummary() {
        return summary(
                List.of(new TaskDueEmailAlertTaskItem(
                        10L,
                        "오늘 작업",
                        OffsetDateTime.parse("2026-05-24T00:00:00Z"),
                        LocalDate.of(2026, 5, 24)
                )),
                List.of()
        );
    }

    private TaskDueEmailAlertSummary summary(
            List<TaskDueEmailAlertTaskItem> dueTodayTasks,
            List<TaskDueEmailAlertTaskItem> overdueTasks
    ) {
        return new TaskDueEmailAlertSummary(
                1L,
                LocalDate.of(2026, 5, 24),
                "Asia/Seoul",
                dueTodayTasks,
                overdueTasks
        );
    }
}
