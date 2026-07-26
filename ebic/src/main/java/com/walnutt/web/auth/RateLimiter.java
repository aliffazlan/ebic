package com.walnutt.web.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory sliding-window limiter, keyed by an arbitrary string (source
 * IP for login attempts). Not distributed/perfect - fine at this project's scale
 * per API_CONTRACT.md ("no need for a distributed limiter at this scale").
 */
public final class RateLimiter {
    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> attemptsByKey = new ConcurrentHashMap<>();

    public RateLimiter(int maxAttempts, Duration window) {
        this(maxAttempts, window, Clock.systemUTC());
    }

    public RateLimiter(int maxAttempts, Duration window, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.clock = clock;
    }

    /** Records an attempt and returns true if the caller is still within the allowed rate. */
    public synchronized boolean tryAcquire(String key) {
        Instant now = clock.instant();
        Deque<Instant> attempts = attemptsByKey.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!attempts.isEmpty() && Duration.between(attempts.peekFirst(), now).compareTo(window) > 0) {
            attempts.pollFirst();
        }
        if (attempts.size() >= maxAttempts) {
            return false;
        }
        attempts.addLast(now);
        return true;
    }
}
