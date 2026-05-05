package com.yegkim.task_reloader_api.auth.security;

public record RateLimitViolation(
        String code,
        String message,
        long retryAfterSeconds
) {
}
