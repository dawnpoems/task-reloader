package com.yegkim.task_reloader_api.task.repository;

import com.yegkim.task_reloader_api.task.entity.Task;
import com.yegkim.task_reloader_api.task.entity.TaskCompletion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Testcontainers
@DisplayName("TaskCompletionRepository JPA 테스트")
class TaskCompletionRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskCompletionRepository taskCompletionRepository;

    @Test
    @DisplayName("특정 작업의 완료 이력을 최신 완료 시각 순으로 조회")
    void findByTaskIdOrderByCompletedAtDesc() {
        OffsetDateTime now = OffsetDateTime.now();
        Task task = taskRepository.save(Task.builder()
                .name("Recurring Task")
                .everyNDays(3)
                .nextDueAt(now.plusDays(3))
                .isActive(true)
                .build());

        Task otherTask = taskRepository.save(Task.builder()
                .name("Other Task")
                .everyNDays(5)
                .nextDueAt(now.plusDays(5))
                .isActive(true)
                .build());

        TaskCompletion older = taskCompletionRepository.save(TaskCompletion.builder()
                .task(task)
                .completedAt(now.minusDays(2))
                .previousDueAt(now.minusDays(2))
                .nextDueAt(now.plusDays(1))
                .build());
        TaskCompletion newer = taskCompletionRepository.save(TaskCompletion.builder()
                .task(task)
                .completedAt(now.minusDays(1))
                .previousDueAt(now.minusDays(1))
                .nextDueAt(now.plusDays(2))
                .build());
        taskCompletionRepository.save(TaskCompletion.builder()
                .task(otherTask)
                .completedAt(now)
                .previousDueAt(now)
                .nextDueAt(now.plusDays(5))
                .build());

        List<TaskCompletion> result = taskCompletionRepository.findByTaskIdOrderByCompletedAtDesc(task.getId());

        assertThat(result).containsExactly(newer, older);
    }
}
