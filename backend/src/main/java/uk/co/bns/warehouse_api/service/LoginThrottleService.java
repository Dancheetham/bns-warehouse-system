package uk.co.bns.warehouse_api.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Increasing-delay login throttling, tracked independently per client IP and
 * per attempted username - JsonLoginFilter checks both and enforces
 * whichever is currently more restrictive. This is deliberately a growing
 * delay rather than a hard lockout: locking an account out after N bad
 * attempts would let anyone deny a genuine user access just by typing their
 * username with the wrong password a few times, which is worse than the
 * problem it solves. Same limitations as SimpleRateLimiter - in-memory,
 * single-instance, resets on restart - which is fine for what this protects
 * against (scripted credential-stuffing / guessing, not a determined
 * attacker with infinite patience).
 */
@Component
public class LoginThrottleService {

    // The first few wrong attempts are free (mistyped passwords happen) -
    // delay only kicks in once a run of failures starts looking automated.
    private static final int FREE_ATTEMPTS = 3;

    // A failure this old no longer counts towards the current run - a wrong
    // password from half an hour ago shouldn't still be slowing down a
    // legitimate attempt now.
    private static final Duration DECAY = Duration.ofMinutes(30);

    private static class Attempt {
        final AtomicInteger failCount = new AtomicInteger();
        volatile long lastFailureMillis = 0;
    }

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    /** Seconds the caller must wait before trying {@code key} again (0 = fine to try now). */
    public long secondsRemaining(String key) {
        Attempt a = attempts.get(key);
        if (a == null) return 0;
        int fails = liveFailCount(a);
        if (fails < FREE_ATTEMPTS) return 0;
        long elapsedMillis = System.currentTimeMillis() - a.lastFailureMillis;
        long remainingMillis = delayMillisFor(fails) - elapsedMillis;
        return remainingMillis > 0 ? (remainingMillis + 999) / 1000 : 0;
    }

    public void recordFailure(String key) {
        Attempt a = attempts.computeIfAbsent(key, k -> new Attempt());
        synchronized (a) {
            if (System.currentTimeMillis() - a.lastFailureMillis > DECAY.toMillis()) {
                a.failCount.set(0);
            }
            a.failCount.incrementAndGet();
            a.lastFailureMillis = System.currentTimeMillis();
        }
    }

    /** Clears any build-up for this key - called on a successful login. */
    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    private int liveFailCount(Attempt a) {
        if (System.currentTimeMillis() - a.lastFailureMillis > DECAY.toMillis()) return 0;
        return a.failCount.get();
    }

    /** 2s, 4s, 8s, 16s, 30s, 30s, ... - doubles from the 4th failure, capped at 30s. */
    private long delayMillisFor(int fails) {
        int over = fails - FREE_ATTEMPTS + 1; // 1, 2, 3, ...
        long seconds = Math.min(30, 1L << Math.min(over, 10));
        return seconds * 1000;
    }
}
