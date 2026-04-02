package com.yegkim.task_reloader_api.task.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

@Entity
@Table(name = "tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Task {

    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false)
    private String name;

    @Column(name = "every_n_days", nullable = false)
    private Integer everyNDays;

    @Column(nullable = false)
    @Builder.Default
    private String timezone = "Asia/Seoul";

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "next_due_at", nullable = false)
    private OffsetDateTime nextDueAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "last_completed_at")
    private OffsetDateTime lastCompletedAt;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public void assignOwner(Long userId) {
        this.userId = userId;
    }

    public void update(String name, Integer everyNDays, Boolean isActive, LocalDate startDate) {
        if (name != null) this.name = name;
        if (everyNDays != null) this.everyNDays = everyNDays;
        if (isActive != null) this.isActive = isActive;
        if (startDate != null) {
            this.startDate = startDate;
            this.nextDueAt = toStartOfDay(startDate);
        }
    }

    public void complete(Instant now) {
        OffsetDateTime odt = now.atOffset(ZoneOffset.UTC);
        this.completedAt = odt;
        this.lastCompletedAt = odt;
        this.nextDueAt = odt.plusDays(this.everyNDays);
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;

        ZoneId zoneId = resolveZoneId();
        if (this.startDate == null) {
            this.startDate = LocalDate.now(zoneId);
        }
        if (this.nextDueAt == null) {
            this.nextDueAt = toStartOfDay(this.startDate);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    private OffsetDateTime toStartOfDay(LocalDate date) {
        return date.atStartOfDay(resolveZoneId()).toOffsetDateTime();
    }

    private ZoneId resolveZoneId() {
        return this.timezone != null ? ZoneId.of(this.timezone) : DEFAULT_ZONE_ID;
    }
}
