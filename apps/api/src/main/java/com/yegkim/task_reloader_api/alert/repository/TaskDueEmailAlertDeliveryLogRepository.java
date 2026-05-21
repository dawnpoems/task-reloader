package com.yegkim.task_reloader_api.alert.repository;

import com.yegkim.task_reloader_api.alert.entity.TaskDueEmailAlertDeliveryLog;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TaskDueEmailAlertDeliveryLogRepository extends JpaRepository<TaskDueEmailAlertDeliveryLog, Long> {

    Optional<TaskDueEmailAlertDeliveryLog> findByUserIdAndLocalDate(Long userId, LocalDate localDate);

    boolean existsByUserIdAndLocalDate(Long userId, LocalDate localDate);

    List<TaskDueEmailAlertDeliveryLog> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select deliveryLog
            from TaskDueEmailAlertDeliveryLog deliveryLog
            where deliveryLog.userId = :userId
              and deliveryLog.localDate = :localDate
            """)
    Optional<TaskDueEmailAlertDeliveryLog> findByUserIdAndLocalDateForUpdate(
            @Param("userId") Long userId,
            @Param("localDate") LocalDate localDate
    );
}
