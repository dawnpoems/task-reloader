package com.yegkim.task_reloader_api.alert.repository;

import com.yegkim.task_reloader_api.alert.entity.TaskDueEmailAlertRecipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskDueEmailAlertRecipientRepository extends JpaRepository<TaskDueEmailAlertRecipient, Long> {

    List<TaskDueEmailAlertRecipient> findAllByUserIdOrderByCreatedAtAsc(Long userId);

    long countByUserId(Long userId);

    boolean existsByUserIdAndEmailIgnoreCase(Long userId, String email);

    Optional<TaskDueEmailAlertRecipient> findByIdAndUserId(Long id, Long userId);

    long deleteByIdAndUserId(Long id, Long userId);

    long deleteByUserId(Long userId);
}
