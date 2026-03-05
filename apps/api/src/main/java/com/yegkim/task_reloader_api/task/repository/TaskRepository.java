package com.yegkim.task_reloader_api.task.repository;

import com.yegkim.task_reloader_api.task.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByIsActiveTrueAndNextDueAtBefore(OffsetDateTime now);

    List<Task> findAllByIsActiveTrueOrderByNextDueAtAsc();
}

