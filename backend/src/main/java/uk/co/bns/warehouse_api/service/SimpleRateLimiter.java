package uk.co.bns.warehouse_api.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A plain in-memory, per-key fixed-window rate limiter - no external store,
 * consistent with this app being a single-instance deployment (same
 * reasoning as the session-cookie auth in SecurityConfig). Good enough to
 * blunt scripted abuse of an unauthenticated endpoint (currently: forgot-
 * password, to stop it being used to mail-bomb someone's inbox or as a
 * timing side-channel for guessing valid emails/usernames); not a
 * distributed rate limiter, and every counter resets if the app restarts -
 * both fine for what this is protecting.
 */
@Component
public class SimpleRateLimiter {

    private static class Window {
        volatile long windowStartMillis = System.currentTimeMillis();
        final AtomicInteger count = new AtomicInteger();
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * Returns true if this call is within the allowance for {@code key} in
     * the current window, incrementing its count either way. Once a key's
     * window has passed, it resets clean rather than sliding.
     */
    public boolean allow(String key, int maxAttempts, Duration window) {
        Window w = windows.computeIfAbsent(key, k -> new Window());
        long windowMillis = window.toMillis();
        synchronized (w) {
            long now = System.currentTimeMillis();
            if (now - w.windowStartMillis > windowMillis) {
                w.windowStartMillis = now;
                w.count.set(0);
            }
            return w.count.incrementAndGet() <= maxAttempts;
        }
    }
}
