package com.yegkim.task_reloader_api.alert.mail;

import com.yegkim.task_reloader_api.alert.config.TaskDueEmailAlertMailConfig;
import com.yegkim.task_reloader_api.alert.config.TaskDueEmailAlertMailTemplateConfig;
import com.yegkim.task_reloader_api.alert.dto.TaskDueEmailAlertMailContent;
import com.yegkim.task_reloader_api.alert.dto.TaskDueEmailAlertSummary;
import com.yegkim.task_reloader_api.alert.dto.TaskDueEmailAlertTaskItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TaskDueEmailAlertMailTemplateRenderer 단위테스트")
class TaskDueEmailAlertMailTemplateRendererTest {

    @Test
    @DisplayName("Thymeleaf 템플릿 파일로 제목과 HTML/plain text 본문을 렌더링한다")
    void renderTemplateFiles() {
        TaskDueEmailAlertMailTemplateRenderer renderer = renderer("https://task.example.com/");
        TaskDueEmailAlertSummary summary = summary(
                List.of(item(10L, "오늘 작업 <중요>", LocalDate.of(2026, 5, 24))),
                List.of(item(20L, "지난 작업", LocalDate.of(2026, 5, 23)))
        );

        TaskDueEmailAlertMailContent content = renderer.render(summary);

        assertThat(content.subject()).isEqualTo("[Task Reloader] 오늘 마감 1개, 지난 작업 1개");
        assertThat(content.htmlBody())
                .contains("작업 마감 알림")
                .contains("2026-05-24")
                .contains("Asia/Seoul")
                .contains("https://task.example.com/tasks/10")
                .contains("https://task.example.com/tasks/20")
                .contains("오늘 작업 &lt;중요&gt;")
                .doesNotContain("오늘 작업 <중요>");
        assertThat(content.textBody())
                .contains("오늘 마감: 1개")
                .contains("지난 작업: 1개")
                .contains("- 오늘 작업 <중요> (마감일: 2026-05-24, 링크: https://task.example.com/tasks/10)")
                .contains("- 지난 작업 (마감일: 2026-05-23, 링크: https://task.example.com/tasks/20)");
    }

    @Test
    @DisplayName("대상 작업이 없으면 템플릿에 빈 상태 문구를 렌더링한다")
    void renderEmptySections() {
        TaskDueEmailAlertMailTemplateRenderer renderer = renderer("https://task.example.com");
        TaskDueEmailAlertSummary summary = summary(List.of(), List.of());

        TaskDueEmailAlertMailContent content = renderer.render(summary);

        assertThat(content.subject()).isEqualTo("[Task Reloader] 오늘 마감 0개, 지난 작업 0개");
        assertThat(content.htmlBody())
                .contains("아직 오늘 처리할 작업이 없습니다.")
                .contains("밀린 작업이 없습니다.");
        assertThat(content.textBody())
                .contains("오늘 마감: 0개")
                .contains("지난 작업: 0개")
                .contains("- 없음");
    }

    private TaskDueEmailAlertMailTemplateRenderer renderer(String appUrl) {
        TemplateEngine templateEngine = new SpringTemplateEngine();
        Set<ITemplateResolver> resolvers = new LinkedHashSet<>();
        resolvers.add(new TaskDueEmailAlertMailTemplateConfig().taskDueEmailAlertTextTemplateResolver());
        resolvers.add(htmlTemplateResolver());
        templateEngine.setTemplateResolvers(resolvers);
        return new TaskDueEmailAlertMailTemplateRenderer(
                templateEngine,
                new TaskDueEmailAlertMailConfig("no-reply@task-reloader.local", appUrl)
        );
    }

    private ITemplateResolver htmlTemplateResolver() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCheckExistence(true);
        resolver.setCacheable(false);
        resolver.setOrder(2);
        return resolver;
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

    private TaskDueEmailAlertTaskItem item(Long taskId, String name, LocalDate dueDate) {
        return new TaskDueEmailAlertTaskItem(
                taskId,
                name,
                OffsetDateTime.parse(dueDate + "T00:00:00Z"),
                dueDate
        );
    }
}
