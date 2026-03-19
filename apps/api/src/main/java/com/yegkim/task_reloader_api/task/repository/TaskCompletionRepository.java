package com.yegkim.task_reloader_api.task.repository;

import com.yegkim.task_reloader_api.task.entity.TaskCompletion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskCompletionRepository extends JpaRepository<TaskCompletion, Long> {

    List<TaskCompletion> findByTaskIdOrderByCompletedAtDesc(Long taskId);
}
