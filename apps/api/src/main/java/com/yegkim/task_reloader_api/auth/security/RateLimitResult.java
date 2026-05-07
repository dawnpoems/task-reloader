package com.yegkim.task_reloader_api.auth.security;

public record RateLimitResult(boolean allowed, long retryAfterSeconds) {

    public static RateLimitResult granted() {
        return new RateLimitResult(true, 0);
    }

    public static RateLimitResult blocked(long retryAfterSeconds) {
        long normalizedRetryAfter = Math.max(1, retryAfterSeconds);
        return new RateLimitResult(false, normalizedRetryAfter);
    }
}
