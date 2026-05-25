package com.yegkim.task_reloader_api.alert.repository;

import com.yegkim.task_reloader_api.alert.entity.TaskDueEmailAlertSetting;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface TaskDueEmailAlertSettingRepository extends JpaRepository<TaskDueEmailAlertSetting, Long> {

    List<TaskDueEmailAlertSetting> findAllByEnabledTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TaskDueEmailAlertSetting s where s.userId = :userId")
    Optional<TaskDueEmailAlertSetting> findByUserIdForUpdate(@Param("userId") Long userId);

    @Query(
            value = """
                    SELECT *
                    FROM task_due_email_alert_settings
                    WHERE enabled = TRUE
                      AND next_send_at IS NOT NULL
                      AND next_send_at <= :now
                    ORDER BY next_send_at ASC
                    LIMIT :batchSize
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true
    )
    List<TaskDueEmailAlertSetting> findDueSettingsForUpdate(
            @Param("now") OffsetDateTime now,
            @Param("batchSize") int batchSize
    );
}
