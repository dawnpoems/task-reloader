package com.yegkim.task_reloader_api.alert.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "task_due_email_alert_delivery_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TaskDueEmailAlertDeliveryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "local_date", nullable = false, updatable = false)
    private LocalDate localDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskDueEmailAlertDeliveryStatus status;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 1;

    @Column(name = "recipient_count", nullable = false)
    @Builder.Default
    private int recipientCount = 0;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static TaskDueEmailAlertDeliveryLog create(
            Long userId,
            LocalDate localDate,
            TaskDueEmailAlertDeliveryStatus status,
            int attemptCount,
            int recipientCount,
            String errorMessage
    ) {
        return TaskDueEmailAlertDeliveryLog.builder()
                .userId(userId)
                .localDate(localDate)
                .status(status)
                .attemptCount(attemptCount)
                .recipientCount(recipientCount)
                .errorMessage(errorMessage)
                .build();
    }

    public void recordResult(
            TaskDueEmailAlertDeliveryStatus status,
            int attemptCount,
            int recipientCount,
            String errorMessage
    ) {
        this.status = status;
        this.attemptCount = attemptCount;
        this.recipientCount = recipientCount;
        this.errorMessage = errorMessage;
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
