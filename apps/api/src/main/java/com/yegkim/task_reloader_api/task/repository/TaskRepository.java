package com.yegkim.task_reloader_api.task.repository;

import com.yegkim.task_reloader_api.task.entity.Task;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByIsActiveTrueAndNextDueAtBefore(OffsetDateTime now);

    List<Task> findAllByIsActiveTrueOrderByNextDueAtAsc();

    List<Task> findAllByUserIdAndIsActiveTrueOrderByNextDueAtAsc(Long userId);

    Optional<Task> findByIdAndUserId(Long id, Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Task t where t.id = :id")
    Optional<Task> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Task t where t.id = :id and t.userId = :userId")
    Optional<Task> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);
}
