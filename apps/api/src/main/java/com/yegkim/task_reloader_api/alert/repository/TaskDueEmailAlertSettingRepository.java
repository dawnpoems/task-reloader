package com.yegkim.task_reloader_api.alert.repository;

import com.yegkim.task_reloader_api.alert.entity.TaskDueEmailAlertSetting;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TaskDueEmailAlertSettingRepository extends JpaRepository<TaskDueEmailAlertSetting, Long> {

    List<TaskDueEmailAlertSetting> findAllByEnabledTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TaskDueEmailAlertSetting s where s.userId = :userId")
    Optional<TaskDueEmailAlertSetting> findByUserIdForUpdate(@Param("userId") Long userId);
}
