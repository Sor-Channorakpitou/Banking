package com.example.bank.security;

import com.example.bank.config.LoginRateLimitProperties;
import com.example.bank.exception.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing with a sliding window of recent failed logins,
 * counted two ways:
 * <ul>
 *   <li>per email + IP: stops guessing one account's password. Including the IP
 *       means an attacker can't lock the real user out from somewhere else.</li>
 *   <li>per IP: stops one source from trying many accounts (credential stuffing).</li>
 * </ul>
 * The check runs <em>before</em> the BCrypt comparison, so blocked attempts cost the
 * server almost nothing. State lives in memory, so it only works for a single
 * instance; with several instances you'd keep the counters in Redis (e.g. Bucket4j).
 */
@Component
public class LoginRateLimiter {

    /** Housekeeping threshold: drop idle keys once the map gets this big. */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final LoginRateLimitProperties properties;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    @Autowired
    public LoginRateLimiter(LoginRateLimitProperties properties) {
        this(properties, Clock.systemUTC());
    }

    LoginRateLimiter(LoginRateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Throws if this email/IP pair or this IP has too many recent failures. */
    public void checkAllowed(String email, String ip) {
        Instant now = clock.instant();
        check(accountKey(email, ip), properties.maxFailuresPerAccount(), now);
        check(ipKey(ip), properties.maxFailuresPerIp(), now);
    }

    public void recordFailure(String email, String ip) {
        Instant now = clock.instant();
        add(accountKey(email, ip), now);
        add(ipKey(ip), now);
        if (failures.size() > CLEANUP_THRESHOLD) {
            failures.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    prune(e.getValue(), now);
                    return e.getValue().isEmpty();
                }
            });
        }
    }

    /** A correct password clears that account's counter (but not the IP's). */
    public void recordSuccess(String email, String ip) {
        failures.remove(accountKey(email, ip));
    }

    private void check(String key, int max, Instant now) {
        Deque<Instant> attempts = failures.get(key);
        if (attempts == null) {
            return;
        }
        synchronized (attempts) {
            prune(attempts, now);
            if (attempts.size() >= max) {
                Duration retryAfter = Duration.between(now, attempts.peekFirst().plus(properties.window()));
                throw new RateLimitExceededException(Math.max(1, retryAfter.toSeconds()));
            }
        }
    }

    private void add(String key, Instant now) {
        Deque<Instant> attempts = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (attempts) {
            prune(attempts, now);
            attempts.addLast(now);
        }
    }

    private void prune(Deque<Instant> attempts, Instant now) {
        Instant cutoff = now.minus(properties.window());
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(cutoff)) {
            attempts.pollFirst();
        }
    }

    private static String accountKey(String email, String ip) {
        return "acct:" + email + "|" + ip;
    }

    private static String ipKey(String ip) {
        return "ip:" + ip;
    }
}
