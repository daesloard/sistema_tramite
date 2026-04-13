package com.sistema.tramites.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class LoginLockoutService {

    private final ConcurrentHashMap<String, AttemptState> attempts = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupMillis = new AtomicLong(0L);

    @Value("${app.auth.lockout.enabled:true}")
    private boolean enabled;

    @Value("${app.auth.lockout.max-failures:5}")
    private int maxFailures;

    @Value("${app.auth.lockout.block-seconds:120}")
    private long blockSeconds;

    @Value("${app.auth.lockout.window-seconds:600}")
    private long windowSeconds;

    @Value("${app.auth.lockout.cleanup-interval-seconds:300}")
    private long cleanupIntervalSeconds;

    public LockoutStatus currentStatus(String username, String clientIp) {
        if (!enabled) {
            return new LockoutStatus(false, 0L);
        }

        long now = System.currentTimeMillis();
        String key = buildKey(username, clientIp);
        AttemptState state = attempts.get(key);
        if (state == null) {
            return new LockoutStatus(false, 0L);
        }

        synchronized (state) {
            state.lastSeenMillis = now;
            if (state.blockedUntilMillis > now) {
                long retryAfter = Math.max(1L, (long) Math.ceil((state.blockedUntilMillis - now) / 1000.0));
                return new LockoutStatus(true, retryAfter);
            }
            if (state.windowStartMillis + (windowSeconds * 1000L) <= now) {
                state.windowStartMillis = now;
                state.failures = 0;
            }
            return new LockoutStatus(false, 0L);
        }
    }

    public void registerFailure(String username, String clientIp) {
        if (!enabled) {
            return;
        }

        long now = System.currentTimeMillis();
        String key = buildKey(username, clientIp);
        AttemptState state = attempts.computeIfAbsent(key, unused -> new AttemptState(now));

        synchronized (state) {
            if (state.windowStartMillis + (windowSeconds * 1000L) <= now) {
                state.windowStartMillis = now;
                state.failures = 0;
            }

            state.failures += 1;
            state.lastSeenMillis = now;
            if (state.failures >= Math.max(1, maxFailures)) {
                state.blockedUntilMillis = now + (Math.max(1L, blockSeconds) * 1000L);
            }
        }

        cleanupIfNeeded(now);
    }

    public void registerSuccess(String username, String clientIp) {
        if (!enabled) {
            return;
        }

        attempts.remove(buildKey(username, clientIp));
    }

    private String buildKey(String username, String clientIp) {
        String user = username == null ? "unknown-user" : username.trim().toLowerCase();
        String ip = clientIp == null || clientIp.isBlank() ? "unknown-ip" : clientIp.trim();
        return user + "|" + ip;
    }

    private void cleanupIfNeeded(long nowMillis) {
        long intervalMillis = Math.max(1L, cleanupIntervalSeconds) * 1000L;
        long previous = lastCleanupMillis.get();
        if ((nowMillis - previous) < intervalMillis || !lastCleanupMillis.compareAndSet(previous, nowMillis)) {
            return;
        }

        long keepMillis = Math.max(windowSeconds, blockSeconds) * 1000L * 2;
        attempts.entrySet().removeIf(entry -> {
            AttemptState state = entry.getValue();
            return (nowMillis - state.lastSeenMillis) > keepMillis && state.blockedUntilMillis <= nowMillis;
        });
    }

    private static final class AttemptState {
        private long windowStartMillis;
        private int failures;
        private long blockedUntilMillis;
        private long lastSeenMillis;

        private AttemptState(long nowMillis) {
            this.windowStartMillis = nowMillis;
            this.failures = 0;
            this.blockedUntilMillis = 0L;
            this.lastSeenMillis = nowMillis;
        }
    }

    public record LockoutStatus(boolean blocked, long retryAfterSeconds) {
    }
}