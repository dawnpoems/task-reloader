package com.yegkim.task_reloader_api.alert.mail;

import com.yegkim.task_reloader_api.alert.config.TaskDueEmailAlertMailConfig;
import com.yegkim.task_reloader_api.alert.dto.TaskDueEmailAlertMailContent;
import com.yegkim.task_reloader_api.alert.dto.TaskDueEmailAlertSummary;
import com.yegkim.task_reloader_api.alert.dto.TaskDueEmailAlertTaskItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TaskDueEmailAlertMailTemplateRenderer {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final TaskDueEmailAlertMailConfig mailConfig;

    public TaskDueEmailAlertMailContent render(TaskDueEmailAlertSummary summary) {
        String subject = subject(summary);
        return new TaskDueEmailAlertMailContent(
                subject,
                renderHtml(summary),
                renderText(summary)
        );
    }

    private String subject(TaskDueEmailAlertSummary summary) {
        return String.format(
                "[Task Reloader] 오늘 마감 %d개, 지난 작업 %d개",
                summary.dueTodayCount(),
                summary.overdueCount()
        );
    }

    private String renderHtml(TaskDueEmailAlertSummary summary) {
        StringBuilder html = new StringBuilder();
        html.append("""
                <!doctype html>
                <html lang="ko">
                <head>
                  <meta charset="UTF-8">
                  <title>Task Reloader 알림</title>
                </head>
                <body style="margin:0;padding:0;background:#f6f7f9;color:#1f2933;font-family:Arial,sans-serif;">
                  <main style="max-width:640px;margin:0 auto;padding:32px 20px;">
                    <section style="background:#ffffff;border:1px solid #d9dee7;border-radius:8px;padding:24px;">
                """);
        html.append("<h1 style=\"margin:0 0 8px;font-size:22px;line-height:1.35;color:#111827;\">작업 마감 알림</h1>");
        html.append("<p style=\"margin:0 0 20px;color:#52606d;font-size:14px;line-height:1.6;\">");
        html.append(escape(summary.localDate().format(DATE_FORMATTER)));
        html.append(" 기준, ");
        html.append(escape(summary.timezone()));
        html.append(" 타임존으로 정리했습니다.</p>");
        html.append(summaryBox(summary));
        html.append(taskSection("오늘 마감", summary.dueTodayTasks(), "아직 오늘 처리할 작업이 없습니다."));
        html.append(taskSection("지난 작업", summary.overdueTasks(), "밀린 작업이 없습니다."));
        html.append("""
                    </section>
                  </main>
                </body>
                </html>
                """);
        return html.toString();
    }

    private String summaryBox(TaskDueEmailAlertSummary summary) {
        return """
                <div style="display:flex;gap:12px;margin:0 0 24px;">
                  <div style="flex:1;border:1px solid #d9dee7;border-radius:8px;padding:14px;">
                    <div style="font-size:12px;color:#66788a;">오늘 마감</div>
                    <div style="font-size:24px;font-weight:700;color:#111827;">%d</div>
                  </div>
                  <div style="flex:1;border:1px solid #d9dee7;border-radius:8px;padding:14px;">
                    <div style="font-size:12px;color:#66788a;">지난 작업</div>
                    <div style="font-size:24px;font-weight:700;color:#111827;">%d</div>
                  </div>
                </div>
                """.formatted(summary.dueTodayCount(), summary.overdueCount());
    }

    private String taskSection(String title, List<TaskDueEmailAlertTaskItem> tasks, String emptyMessage) {
        StringBuilder html = new StringBuilder();
        html.append("<h2 style=\"margin:24px 0 10px;font-size:16px;color:#111827;\">");
        html.append(escape(title));
        html.append("</h2>");

        if (tasks.isEmpty()) {
            html.append("<p style=\"margin:0;color:#66788a;font-size:14px;line-height:1.6;\">");
            html.append(escape(emptyMessage));
            html.append("</p>");
            return html.toString();
        }

        html.append("<ul style=\"list-style:none;margin:0;padding:0;\">");
        for (TaskDueEmailAlertTaskItem task : tasks) {
            html.append("<li style=\"border-top:1px solid #e5e9f0;padding:12px 0;\">");
            html.append("<a href=\"");
            html.append(escapeAttribute(taskUrl(task.taskId())));
            html.append("\" style=\"font-size:15px;font-weight:700;color:#1d4ed8;text-decoration:none;\">");
            html.append(escape(task.name()));
            html.append("</a>");
            html.append("<div style=\"margin-top:4px;color:#66788a;font-size:13px;\">마감일: ");
            html.append(escape(task.dueDate().format(DATE_FORMATTER)));
            html.append("</div>");
            html.append("</li>");
        }
        html.append("</ul>");
        return html.toString();
    }

    private String renderText(TaskDueEmailAlertSummary summary) {
        StringBuilder text = new StringBuilder();
        text.append("Task Reloader 작업 마감 알림\n");
        text.append("기준일: ").append(summary.localDate().format(DATE_FORMATTER)).append("\n");
        text.append("타임존: ").append(summary.timezone()).append("\n\n");
        text.append("오늘 마감: ").append(summary.dueTodayCount()).append("개\n");
        appendTextSection(text, summary.dueTodayTasks(), "오늘 마감");
        text.append("\n지난 작업: ").append(summary.overdueCount()).append("개\n");
        appendTextSection(text, summary.overdueTasks(), "지난 작업");
        return text.toString();
    }

    private void appendTextSection(StringBuilder text, List<TaskDueEmailAlertTaskItem> tasks, String title) {
        if (tasks.isEmpty()) {
            text.append("- 없음\n");
            return;
        }

        for (TaskDueEmailAlertTaskItem task : tasks) {
            text.append("- ")
                    .append(task.name())
                    .append(" (마감일: ")
                    .append(task.dueDate().format(DATE_FORMATTER))
                    .append(", 링크: ")
                    .append(taskUrl(task.taskId()))
                    .append(")\n");
        }
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

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String escapeAttribute(String value) {
        return escape(value)
                .replace("\"", "&quot;");
    }
}
