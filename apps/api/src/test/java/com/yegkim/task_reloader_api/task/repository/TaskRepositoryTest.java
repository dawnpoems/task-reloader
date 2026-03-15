package com.yegkim.task_reloader_api.task.repository;

import com.yegkim.task_reloader_api.task.entity.Task;
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
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Testcontainers
@DisplayName("TaskRepository JPA 테스트")
class TaskRepositoryTest {

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

    @Test
    @DisplayName("활성 여부와 다음 기한 기준으로 조회")
    void findByIsActiveTrueAndNextDueAtBefore() {
        OffsetDateTime now = OffsetDateTime.now();
        Task activePast = Task.builder()
                .name("Active Past")
                .everyNDays(1)
                .nextDueAt(now.minusHours(1))
                .isActive(true)
                .build();
        Task activeFuture = Task.builder()
                .name("Active Future")
                .everyNDays(1)
                .nextDueAt(now.plusHours(2))
                .isActive(true)
                .build();
        Task inactivePast = Task.builder()
                .name("Inactive Past")
                .everyNDays(1)
                .nextDueAt(now.minusHours(2))
                .isActive(false)
                .build();

        Task savedActivePast = taskRepository.save(activePast);
        taskRepository.save(activeFuture);
        taskRepository.save(inactivePast);

        List<Task> result = taskRepository.findByIsActiveTrueAndNextDueAtBefore(now);

        assertThat(result).containsExactly(savedActivePast);
    }

    @Test
    @DisplayName("활성 작업을 다음 기한 순으로 정렬하여 반환")
    void findAllByIsActiveTrueOrderByNextDueAtAsc() {
        OffsetDateTime now = OffsetDateTime.now();
        Task first = Task.builder()
                .name("First")
                .everyNDays(2)
                .nextDueAt(now.plusHours(1))
                .isActive(true)
                .build();
        Task second = Task.builder()
                .name("Second")
                .everyNDays(2)
                .nextDueAt(now.plusHours(3))
                .isActive(true)
                .build();
        Task inactive = Task.builder()
                .name("Inactive")
                .everyNDays(2)
                .nextDueAt(now.plusHours(2))
                .isActive(false)
                .build();

        Task savedFirst = taskRepository.save(first);
        Task savedSecond = taskRepository.save(second);
        taskRepository.save(inactive);

        List<Task> result = taskRepository.findAllByIsActiveTrueOrderByNextDueAtAsc();

        assertThat(result).containsExactly(savedFirst, savedSecond);
    }

    @Test
    @DisplayName("startDate가 있으면 nextDueAt을 시작일 00:00으로 초기화")
    void saveWithStartDateInitializesNextDueAtFromStartDate() {
        LocalDate startDate = LocalDate.of(2026, 4, 1);

        Task task = Task.builder()
                .name("Task with start date")
                .everyNDays(3)
                .startDate(startDate)
                .isActive(true)
                .build();

        Task saved = taskRepository.saveAndFlush(task);

        assertThat(saved.getStartDate()).isEqualTo(startDate);
        assertThat(saved.getNextDueAt()).isEqualTo(OffsetDateTime.parse("2026-04-01T00:00:00+09:00"));
    }

    @Test
    @DisplayName("startDate 미입력 시 오늘 날짜 기본값 + nextDueAt은 00:00")
    void saveWithoutStartDateDefaultsToToday() {
        Task task = Task.builder()
                .name("Task without start date")
                .everyNDays(3)
                .isActive(true)
                .build();

        Task saved = taskRepository.saveAndFlush(task);

        assertThat(saved.getStartDate()).isNotNull();
        assertThat(saved.getNextDueAt().toLocalDate()).isEqualTo(saved.getStartDate());
        assertThat(saved.getNextDueAt().toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(saved.getNextDueAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
    }
}
