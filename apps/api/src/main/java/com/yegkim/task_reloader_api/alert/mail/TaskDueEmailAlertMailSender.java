package com.yegkim.task_reloader_api.alert.mail;

import com.yegkim.task_reloader_api.alert.config.TaskDueEmailAlertMailConfig;
import com.yegkim.task_reloader_api.alert.dto.TaskDueEmailAlertMailContent;
import com.yegkim.task_reloader_api.alert.dto.TaskDueEmailAlertSummary;
import com.yegkim.task_reloader_api.auth.entity.User;
import com.yegkim.task_reloader_api.auth.repository.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@ConditionalOnProperty(prefix = "spring.mail", name = "host")
@RequiredArgsConstructor
public class TaskDueEmailAlertMailSender {

    private final JavaMailSender javaMailSender;
    private final TaskDueEmailAlertMailTemplateRenderer templateRenderer;
    private final TaskDueEmailAlertMailConfig mailConfig;
    private final UserRepository userRepository;

    public int send(TaskDueEmailAlertSummary summary, List<String> recipients) {
        if (summary.isEmpty() || recipients == null || recipients.isEmpty()) {
            return 0;
        }

        String prefillEmail = userRepository.findById(summary.userId())
                .map(User::getEmail)
                .orElse(null);
        int sentCount = 0;
        for (String recipient : recipients) {
            if (recipient == null || recipient.isBlank()) {
                continue;
            }
            String normalizedRecipient = recipient.trim();
            TaskDueEmailAlertMailContent content = templateRenderer.render(summary, prefillEmail);
            sendOne(normalizedRecipient, content);
            sentCount += 1;
        }
        return sentCount;
    }

    private void sendOne(String recipient, TaskDueEmailAlertMailContent content) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    true,
                    StandardCharsets.UTF_8.name()
            );
            helper.setFrom(mailConfig.from());
            helper.setTo(recipient);
            helper.setSubject(content.subject());
            helper.setText(content.textBody(), content.htmlBody());
            javaMailSender.send(message);
        } catch (MessagingException | MailException ex) {
            throw new TaskDueEmailAlertMailException("작업 마감 이메일 알림 발송에 실패했습니다.", ex);
        }
    }
}
