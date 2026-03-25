package com.yegkim.task_reloader_api.task.repository;

import com.yegkim.task_reloader_api.task.entity.TaskCompletion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface TaskCompletionRepository extends JpaRepository<TaskCompletion, Long> {

    List<TaskCompletion> findByTaskIdOrderByCompletedAtDesc(Long taskId);
    List<TaskCompletion> findByTaskIdAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtDesc(
            Long taskId,
            OffsetDateTime startInclusive,
            OffsetDateTime endExclusive
    );

    List<TaskCompletion> findTop5ByOrderByCompletedAtDesc();

    List<TaskCompletion> findByCompletedAtGreaterThanEqualAndCompletedAtLessThan(
            OffsetDateTime startInclusive,
            OffsetDateTime endExclusive
    );

    long countByCompletedAtBetween(OffsetDateTime start, OffsetDateTime end);

    long countByCompletedAtGreaterThanEqual(OffsetDateTime start);
}
