package com.yegkim.task_reloader_api.task.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "every_n_days", nullable = false)
    private Integer everyNDays;

    @Column(nullable = false)
    @Builder.Default
    private String timezone = "Asia/Seoul";

    @Column(name = "next_due_at", nullable = false)
    private OffsetDateTime nextDueAt;

    @Column(name = "last_completed_at")
    private OffsetDateTime lastCompletedAt;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public void update(String name, Integer everyNDays, Boolean isActive) {
        if (name != null) this.name = name;
        if (everyNDays != null) this.everyNDays = everyNDays;
        if (isActive != null) this.isActive = isActive;
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.nextDueAt == null) {
            this.nextDueAt = now.plusDays(this.everyNDays);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}

