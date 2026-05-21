package com.yegkim.task_reloader_api.alert.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.time.LocalTime;
import java.time.OffsetDateTime;

@Entity
@Table(name = "task_due_email_alert_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TaskDueEmailAlertSetting {

    private static final LocalTime DEFAULT_SEND_TIME = LocalTime.of(9, 0);
    private static final String DEFAULT_TIMEZONE = "Asia/Seoul";

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = false;

    @Column(name = "send_time", nullable = false)
    @Builder.Default
    private LocalTime sendTime = DEFAULT_SEND_TIME;

    @Column(nullable = false, length = 100)
    @Builder.Default
    private String timezone = DEFAULT_TIMEZONE;

    @Column(name = "last_sent_local_date")
    private LocalDate lastSentLocalDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static TaskDueEmailAlertSetting createDefault(Long userId) {
        return TaskDueEmailAlertSetting.builder()
                .userId(userId)
                .build();
    }

    public void update(Boolean enabled, LocalTime sendTime, String timezone) {
        if (enabled != null) {
            this.enabled = enabled;
        }
        if (sendTime != null) {
            this.sendTime = sendTime;
        }
        if (timezone != null) {
            this.timezone = timezone;
        }
    }

    public void markSent(LocalDate localDate) {
        this.lastSentLocalDate = localDate;
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (this.sendTime == null) {
            this.sendTime = DEFAULT_SEND_TIME;
        }
        if (this.timezone == null) {
            this.timezone = DEFAULT_TIMEZONE;
        }
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
