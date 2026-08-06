package com.cognologix.fpa.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory rate limiter for {@code POST /api/auth/refresh} — 10 calls per hour per user (ADR-056).
 */
@Component
public class RefreshTokenRateLimiter {

    public static final int LIMIT = 10;
    public static final Duration WINDOW = Duration.ofHours(1);

    private final Map<String, Deque<Instant>> attemptsByEmail = new ConcurrentHashMap<>();

    public boolean tryAcquire(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        String key = email.trim().toLowerCase();
        Instant now = Instant.now();
        Instant cutoff = now.minus(WINDOW);
        Deque<Instant> deque = attemptsByEmail.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            prune(deque, cutoff);
            if (deque.size() >= LIMIT) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }

    /** Test helper — clears all recorded attempts. */
    public void clear() {
        attemptsByEmail.clear();
    }

    private static void prune(Deque<Instant> deque, Instant cutoff) {
        Iterator<Instant> it = deque.iterator();
        while (it.hasNext()) {
            if (it.next().isBefore(cutoff)) {
                it.remove();
            } else {
                break;
            }
        }
    }
}
