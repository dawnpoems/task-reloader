package com.yegkim.task_reloader_api.alert.mail;

import com.yegkim.task_reloader_api.alert.config.TaskDueEmailAlertMailConfig;
import com.yegkim.task_reloader_api.alert.dto.TaskDueEmailAlertMailContent;
import com.yegkim.task_reloader_api.alert.dto.TaskDueEmailAlertSummary;
import com.yegkim.task_reloader_api.alert.dto.TaskDueEmailAlertTaskItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TaskDueEmailAlertMailTemplateRenderer {

    private static final String HTML_TEMPLATE = "mail/task-due-email-alert";
    private static final String TEXT_TEMPLATE = "mail/task-due-email-alert.txt";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final TemplateEngine templateEngine;
    private final TaskDueEmailAlertMailConfig mailConfig;

    public TaskDueEmailAlertMailContent render(TaskDueEmailAlertSummary summary) {
        String subject = subject(summary);
        Context context = templateContext(summary);
        return new TaskDueEmailAlertMailContent(
                subject,
                templateEngine.process(HTML_TEMPLATE, context),
                templateEngine.process(TEXT_TEMPLATE, context)
        );
    }

    private String subject(TaskDueEmailAlertSummary summary) {
        return String.format(
                "[Task Reloader] 오늘 마감 %d개, 지난 작업 %d개",
                summary.dueTodayCount(),
                summary.overdueCount()
        );
    }

    private Context templateContext(TaskDueEmailAlertSummary summary) {
        Context context = new Context();
        context.setVariable("localDate", summary.localDate().format(DATE_FORMATTER));
        context.setVariable("timezone", summary.timezone());
        context.setVariable("appUrl", normalizedAppUrl());
        context.setVariable("dueTodayCount", summary.dueTodayCount());
        context.setVariable("overdueCount", summary.overdueCount());
        context.setVariable("dueTodayTasks", toTemplateTasks(summary.dueTodayTasks()));
        context.setVariable("overdueTasks", toTemplateTasks(summary.overdueTasks()));
        return context;
    }

    private List<TemplateTask> toTemplateTasks(List<TaskDueEmailAlertTaskItem> tasks) {
        return tasks.stream()
                .map(task -> new TemplateTask(
                        task.name(),
                        task.dueDate().format(DATE_FORMATTER),
                        taskUrl(task.taskId())
                ))
                .toList();
    }

    private String taskUrl(Long taskId) {
        return normalizedAppUrl() + "/tasks/" + taskId;
    }

    private String normalizedAppUrl() {
        String appUrl = mailConfig.appUrl();
        while (appUrl.endsWith("/")) {
            appUrl = appUrl.substring(0, appUrl.length() - 1);
        }
        return appUrl;
    }

    public record TemplateTask(
            String name,
            String dueDate,
            String url
    ) {
    }
}
