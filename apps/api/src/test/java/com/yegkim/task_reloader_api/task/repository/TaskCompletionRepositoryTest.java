package com.yegkim.task_reloader_api.task.repository;

import com.yegkim.task_reloader_api.task.dto.RecentTaskCompletionResponse;
import com.yegkim.task_reloader_api.task.dto.TaskCompletionInsightRow;
import com.yegkim.task_reloader_api.task.entity.Task;
import com.yegkim.task_reloader_api.task.entity.TaskCompletion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private static final long TEST_USER_ID = 1L;

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
    private TaskRepository taskRepository;

    @Autowired
    private TaskCompletionRepository taskCompletionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("특정 작업의 완료 이력을 최신 완료 시각 순으로 조회")
    void findByTaskIdOrderByCompletedAtDesc() {
        OffsetDateTime now = OffsetDateTime.now();
        Task task = taskRepository.save(Task.builder()
                .userId(TEST_USER_ID)
                .name("Recurring Task")
                .everyNDays(3)
                .nextDueAt(now.plusDays(3))
                .isActive(true)
                .build());

        Task otherTask = taskRepository.save(Task.builder()
                .userId(TEST_USER_ID)
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
                .userId(TEST_USER_ID)
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
                .userId(TEST_USER_ID)
                .name("Task A")
                .everyNDays(3)
                .nextDueAt(now.plusDays(3))
                .isActive(true)
                .build());
        Task taskB = taskRepository.save(Task.builder()
                .userId(TEST_USER_ID)
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

    @Test
    @DisplayName("사용자의 오늘 완료 이력을 최신 완료 시각 순으로 조회")
    void findByUserIdAndCompletedAtRangeOrderByCompletedAtDesc() {
        Long otherUserId = jdbcTemplate.queryForObject("""
                INSERT INTO users (email, password_hash, role, status, created_at, updated_at)
                VALUES ('other-user@example.com', 'hash', 'USER', 'APPROVED', NOW(), NOW())
                RETURNING id
                """, Long.class);

        OffsetDateTime todayStart = OffsetDateTime.parse("2026-05-27T15:00:00Z");
        OffsetDateTime tomorrowStart = OffsetDateTime.parse("2026-05-28T15:00:00Z");

        Task task = taskRepository.save(Task.builder()
                .userId(TEST_USER_ID)
                .name("Today Task")
                .everyNDays(3)
                .nextDueAt(todayStart.plusDays(3))
                .isActive(true)
                .build());
        Task otherUserTask = taskRepository.save(Task.builder()
                .userId(otherUserId)
                .name("Other User Task")
                .everyNDays(3)
                .nextDueAt(todayStart.plusDays(3))
                .isActive(true)
                .build());

        taskCompletionRepository.save(TaskCompletion.builder()
                .task(task)
                .completedAt(todayStart.minusSeconds(1))
                .previousDueAt(todayStart.minusDays(1))
                .nextDueAt(todayStart.plusDays(2))
                .build());
        TaskCompletion older = taskCompletionRepository.save(TaskCompletion.builder()
                .task(task)
                .completedAt(todayStart.plusHours(1))
                .previousDueAt(todayStart)
                .nextDueAt(todayStart.plusDays(3))
                .build());
        TaskCompletion newer = taskCompletionRepository.save(TaskCompletion.builder()
                .task(task)
                .completedAt(todayStart.plusHours(8))
                .previousDueAt(todayStart.plusHours(7))
                .nextDueAt(todayStart.plusDays(3))
                .build());
        taskCompletionRepository.save(TaskCompletion.builder()
                .task(otherUserTask)
                .completedAt(todayStart.plusHours(9))
                .previousDueAt(todayStart)
                .nextDueAt(todayStart.plusDays(3))
                .build());
        taskCompletionRepository.save(TaskCompletion.builder()
                .task(task)
                .completedAt(tomorrowStart)
                .previousDueAt(todayStart)
                .nextDueAt(tomorrowStart.plusDays(3))
                .build());

        List<TaskCompletion> result = taskCompletionRepository
                .findByUserIdAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtDesc(
                        TEST_USER_ID, todayStart, tomorrowStart
                );
        long count = taskCompletionRepository
                .countByUserIdAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                        TEST_USER_ID, todayStart, tomorrowStart
                );

        assertThat(result).containsExactly(newer, older);
        assertThat(count).isEqualTo(2);

        List<RecentTaskCompletionResponse> projectionResult = taskCompletionRepository
                .findRecentCompletionResponsesByUserIdAndCompletedAtRange(
                        TEST_USER_ID, todayStart, tomorrowStart
                );

        assertThat(projectionResult).extracting(RecentTaskCompletionResponse::getId)
                .containsExactly(newer.getId(), older.getId());
        assertThat(projectionResult).extracting(RecentTaskCompletionResponse::getTaskId)
                .containsExactly(task.getId(), task.getId());
        assertThat(projectionResult).extracting(RecentTaskCompletionResponse::getTaskName)
                .containsExactly(task.getName(), task.getName());
    }

    @Test
    @DisplayName("인사이트 overview projection은 작업 정보를 조인해서 기간 내 완료 행을 반환")
    void findInsightRowsByUserIdAndCompletedAtRange() {
        Long otherUserId = jdbcTemplate.queryForObject("""
                INSERT INTO users (email, password_hash, role, status, created_at, updated_at)
                VALUES ('insight-row-other-user@example.com', 'hash', 'USER', 'APPROVED', NOW(), NOW())
                RETURNING id
                """, Long.class);

        OffsetDateTime start = OffsetDateTime.parse("2026-05-01T00:00:00Z");
        OffsetDateTime end = OffsetDateTime.parse("2026-06-01T00:00:00Z");

        Task task = taskRepository.save(Task.builder()
                .userId(TEST_USER_ID)
                .name("Insight Row Task")
                .everyNDays(3)
                .nextDueAt(start.plusDays(3))
                .isActive(true)
                .build());
        Task otherUserTask = taskRepository.save(Task.builder()
                .userId(otherUserId)
                .name("Other User Insight Row Task")
                .everyNDays(3)
                .nextDueAt(start.plusDays(3))
                .isActive(true)
                .build());

        TaskCompletion older = taskCompletionRepository.save(TaskCompletion.builder()
                .task(task)
                .completedAt(start.plusDays(1))
                .previousDueAt(start)
                .nextDueAt(start.plusDays(3))
                .build());
        TaskCompletion newer = taskCompletionRepository.save(TaskCompletion.builder()
                .task(task)
                .completedAt(start.plusDays(2))
                .previousDueAt(start.plusDays(2))
                .nextDueAt(start.plusDays(5))
                .build());
        taskCompletionRepository.save(TaskCompletion.builder()
                .task(task)
                .completedAt(start.minusSeconds(1))
                .previousDueAt(start.minusDays(1))
                .nextDueAt(start.plusDays(2))
                .build());
        taskCompletionRepository.save(TaskCompletion.builder()
                .task(otherUserTask)
                .completedAt(start.plusDays(3))
                .previousDueAt(start.plusDays(2))
                .nextDueAt(start.plusDays(5))
                .build());

        List<TaskCompletionInsightRow> result = taskCompletionRepository
                .findInsightRowsByUserIdAndCompletedAtRange(TEST_USER_ID, start, end);

        assertThat(result).extracting(TaskCompletionInsightRow::getTaskId)
                .containsExactly(task.getId(), task.getId());
        assertThat(result).extracting(TaskCompletionInsightRow::getTaskName)
                .containsExactly(task.getName(), task.getName());
        assertThat(result).extracting(TaskCompletionInsightRow::getCompletedAt)
                .containsExactly(newer.getCompletedAt(), older.getCompletedAt());
        assertThat(result).extracting(TaskCompletionInsightRow::getPreviousDueAt)
                .containsExactly(newer.getPreviousDueAt(), older.getPreviousDueAt());
    }

    @Test
    @DisplayName("최근 완료 응답 projection은 작업 정보를 조인해서 최신 순으로 반환")
    void findRecentCompletionResponsesByUserId() {
        Long otherUserId = jdbcTemplate.queryForObject("""
                INSERT INTO users (email, password_hash, role, status, created_at, updated_at)
                VALUES ('projection-other-user@example.com', 'hash', 'USER', 'APPROVED', NOW(), NOW())
                RETURNING id
                """, Long.class);

        OffsetDateTime now = OffsetDateTime.parse("2026-05-30T13:00:00Z");
        Task task = taskRepository.save(Task.builder()
                .userId(TEST_USER_ID)
                .name("Projection Task")
                .everyNDays(3)
                .nextDueAt(now.plusDays(3))
                .isActive(true)
                .build());
        Task otherUserTask = taskRepository.save(Task.builder()
                .userId(otherUserId)
                .name("Other User Projection Task")
                .everyNDays(3)
                .nextDueAt(now.plusDays(3))
                .isActive(true)
                .build());

        TaskCompletion older = taskCompletionRepository.save(TaskCompletion.builder()
                .task(task)
                .completedAt(now.minusHours(2))
                .previousDueAt(now.minusDays(1))
                .nextDueAt(now.plusDays(2))
                .build());
        TaskCompletion newer = taskCompletionRepository.save(TaskCompletion.builder()
                .task(task)
                .completedAt(now.minusHours(1))
                .previousDueAt(now.minusHours(2))
                .nextDueAt(now.plusDays(3))
                .build());
        taskCompletionRepository.save(TaskCompletion.builder()
                .task(otherUserTask)
                .completedAt(now)
                .previousDueAt(now.minusHours(1))
                .nextDueAt(now.plusDays(3))
                .build());

        List<RecentTaskCompletionResponse> result = taskCompletionRepository
                .findRecentCompletionResponsesByUserId(TEST_USER_ID, PageRequest.of(0, 5));

        assertThat(result).extracting(RecentTaskCompletionResponse::getId)
                .containsExactly(newer.getId(), older.getId());
        assertThat(result).extracting(RecentTaskCompletionResponse::getTaskId)
                .containsExactly(task.getId(), task.getId());
        assertThat(result).extracting(RecentTaskCompletionResponse::getTaskName)
                .containsExactly(task.getName(), task.getName());
    }
}
