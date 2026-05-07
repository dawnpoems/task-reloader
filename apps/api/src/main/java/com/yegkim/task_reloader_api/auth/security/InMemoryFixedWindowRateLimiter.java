package com.yegkim.task_reloader_api.auth.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
@RequiredArgsConstructor
public class InMemoryFixedWindowRateLimiter {

    private static final int MAX_KEYS_BEFORE_CLEANUP = 10_000;
    private static final long CLEANUP_INTERVAL_SECONDS = 60;

    private final Clock clock;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupEpochSeconds = new AtomicLong(0);

    public RateLimitResult tryConsume(String key, int limit, long windowSeconds) {
        int normalizedLimit = Math.max(1, limit);
        long normalizedWindowSeconds = Math.max(1, windowSeconds);
        long nowEpochSeconds = clock.instant().getEpochSecond();

        maybeCleanup(nowEpochSeconds);

        AtomicReference<RateLimitResult> decisionRef = new AtomicReference<>(RateLimitResult.granted());
        counters.compute(key, (unused, current) -> {
            if (current == null || nowEpochSeconds >= current.windowStartEpochSeconds + current.windowSeconds) {
                decisionRef.set(RateLimitResult.granted());
                return new WindowCounter(nowEpochSeconds, normalizedWindowSeconds, 1);
            }

            if (current.count < normalizedLimit) {
                decisionRef.set(RateLimitResult.granted());
                return new WindowCounter(current.windowStartEpochSeconds, current.windowSeconds, current.count + 1);
            }

            long retryAfter = current.windowStartEpochSeconds + current.windowSeconds - nowEpochSeconds;
            decisionRef.set(RateLimitResult.blocked(retryAfter));
            return current;
        });

        return decisionRef.get();
    }

    private void maybeCleanup(long nowEpochSeconds) {
        if (counters.size() < MAX_KEYS_BEFORE_CLEANUP) {
            return;
        }

        long previousCleanupAt = lastCleanupEpochSeconds.get();
        if (nowEpochSeconds - previousCleanupAt < CLEANUP_INTERVAL_SECONDS) {
            return;
        }

        if (!lastCleanupEpochSeconds.compareAndSet(previousCleanupAt, nowEpochSeconds)) {
            return;
        }

        counters.entrySet().removeIf(entry -> isWindowExpired(entry.getValue(), nowEpochSeconds));
    }

    private boolean isWindowExpired(WindowCounter counter, long nowEpochSeconds) {
        return nowEpochSeconds >= counter.windowStartEpochSeconds + counter.windowSeconds;
    }

    private record WindowCounter(long windowStartEpochSeconds, long windowSeconds, int count) {
    }
}
