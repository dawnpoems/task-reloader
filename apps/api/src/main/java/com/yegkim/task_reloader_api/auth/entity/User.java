package com.yegkim.task_reloader_api.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "failed_login_count", nullable = false)
    @Builder.Default
    private int failedLoginCount = 0;

    @Column(name = "last_failed_login_at")
    private OffsetDateTime lastFailedLoginAt;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public void approve(User approver, OffsetDateTime approvedAt) {
        this.status = UserStatus.APPROVED;
        this.approvedBy = approver;
        this.approvedAt = approvedAt;
    }

    public void reject() {
        this.status = UserStatus.REJECTED;
        this.approvedBy = null;
        this.approvedAt = null;
    }

    public boolean isLoginLocked(OffsetDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void recordLoginFailure(
            OffsetDateTime now,
            int threshold,
            long baseLockSeconds,
            long maxLockSeconds,
            long resetWindowSeconds
    ) {
        if (threshold <= 0 || baseLockSeconds <= 0 || maxLockSeconds <= 0 || resetWindowSeconds <= 0) {
            throw new IllegalArgumentException("Login lock policy values must be positive.");
        }

        if (lastFailedLoginAt == null || lastFailedLoginAt.plusSeconds(resetWindowSeconds).isBefore(now)) {
            failedLoginCount = 0;
        }

        failedLoginCount += 1;
        lastFailedLoginAt = now;

        if (failedLoginCount < threshold) {
            return;
        }

        int overflow = failedLoginCount - threshold;
        long duration = baseLockSeconds;
        for (int i = 0; i < overflow; i++) {
            if (duration >= maxLockSeconds) {
                duration = maxLockSeconds;
                break;
            }
            duration = Math.min(maxLockSeconds, duration * 2);
        }
        lockedUntil = now.plusSeconds(duration);
    }

    public void clearLoginFailureState() {
        failedLoginCount = 0;
        lastFailedLoginAt = null;
        lockedUntil = null;
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
