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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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

    @Test
    @DisplayName("특정 작업의 특정 월 완료 이력만 조회")
    void findByTaskIdAndCompletedAtRangeOrderByCompletedAtDesc() {
        OffsetDateTime now = OffsetDateTime.now();
        Task task = taskRepository.save(Task.builder()
                .name("Recurring Task")
                .everyNDays(3)
                .nextDueAt(now.plusDays(3))
                .isActive(true)
                .build());

        OffsetDateTime marchStart = LocalDate.of(2026, 3, 1)
                .atStartOfDay(ZoneId.of("Asia/Seoul"))
                .toOffsetDateTime();
        OffsetDateTime aprilStart = marchStart.plusMonths(1);

        TaskCompletion inMarchOlder = taskCompletionRepository.save(TaskCompletion.builder()
                .task(task)
                .completedAt(marchStart.plusDays(1))
                .previousDueAt(marchStart)
                .nextDueAt(marchStart.plusDays(4))
                .build());
        TaskCompletion inMarchNewer = taskCompletionRepository.save(TaskCompletion.builder()
                .task(task)
                .completedAt(marchStart.plusDays(20))
                .previousDueAt(marchStart.plusDays(19))
                .nextDueAt(marchStart.plusDays(23))
                .build());
        taskCompletionRepository.save(TaskCompletion.builder()
                .task(task)
                .completedAt(aprilStart.plusDays(1))
                .previousDueAt(aprilStart)
                .nextDueAt(aprilStart.plusDays(4))
                .build());

        List<TaskCompletion> result = taskCompletionRepository
                .findByTaskIdAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtDesc(
                        task.getId(), marchStart, aprilStart
                );

        assertThat(result).containsExactly(inMarchNewer, inMarchOlder);
    }

    @Test
    @DisplayName("완료 시각 기간 조회는 시작 포함/종료 제외 경계를 지킨다")
    void findByCompletedAtRangeStartInclusiveEndExclusive() {
        OffsetDateTime now = OffsetDateTime.now();
        Task taskA = taskRepository.save(Task.builder()
                .name("Task A")
                .everyNDays(3)
                .nextDueAt(now.plusDays(3))
                .isActive(true)
                .build());
        Task taskB = taskRepository.save(Task.builder()
                .name("Task B")
                .everyNDays(5)
                .nextDueAt(now.plusDays(5))
                .isActive(true)
                .build());

        OffsetDateTime start = OffsetDateTime.parse("2026-03-01T00:00:00+00:00");
        OffsetDateTime end = OffsetDateTime.parse("2026-03-02T00:00:00+00:00");

        taskCompletionRepository.save(TaskCompletion.builder()
                .task(taskA)
                .completedAt(start.minusSeconds(1))
                .previousDueAt(start.minusDays(1))
                .nextDueAt(start.plusDays(2))
                .build());
        TaskCompletion atStart = taskCompletionRepository.save(TaskCompletion.builder()
                .task(taskA)
                .completedAt(start)
                .previousDueAt(start.minusDays(1))
                .nextDueAt(start.plusDays(2))
                .build());
        TaskCompletion inBetween = taskCompletionRepository.save(TaskCompletion.builder()
                .task(taskB)
                .completedAt(start.plusHours(12))
                .previousDueAt(start.plusHours(10))
                .nextDueAt(start.plusDays(2))
                .build());
        taskCompletionRepository.save(TaskCompletion.builder()
                .task(taskA)
                .completedAt(end)
                .previousDueAt(end.minusDays(1))
                .nextDueAt(end.plusDays(2))
                .build());
        taskCompletionRepository.save(TaskCompletion.builder()
                .task(taskB)
                .completedAt(end.plusSeconds(1))
                .previousDueAt(end.minusDays(1))
                .nextDueAt(end.plusDays(2))
                .build());

        List<TaskCompletion> result = taskCompletionRepository
                .findByCompletedAtGreaterThanEqualAndCompletedAtLessThan(start, end);

        assertThat(result).containsExactlyInAnyOrder(atStart, inBetween);
    }
}
