package com.yegkim.task_reloader_api.task.repository;

import com.yegkim.task_reloader_api.task.entity.TaskCompletion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface TaskCompletionRepository extends JpaRepository<TaskCompletion, Long> {

    List<TaskCompletion> findByTaskIdOrderByCompletedAtDesc(Long taskId);

    List<TaskCompletion> findTop5ByOrderByCompletedAtDesc();

    long countByCompletedAtBetween(OffsetDateTime start, OffsetDateTime end);

    long countByCompletedAtGreaterThanEqual(OffsetDateTime start);
}
