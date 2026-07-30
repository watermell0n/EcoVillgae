package com.b201.ecovillage.identity.application;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import com.b201.ecovillage.identity.config.AuthenticationProperties;

@Component
public class InMemoryStudentLoginRateLimiter implements StudentLoginRateLimiter {

    private final ConcurrentMap<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();
    private final AtomicLong requestCount = new AtomicLong();
    private final AuthenticationProperties properties;

    public InMemoryStudentLoginRateLimiter(AuthenticationProperties properties) {
        this.properties = properties;
    }

    @Override
    public void check(String requestKey, Instant now) {
        if ((requestCount.incrementAndGet() & 255) == 0) {
            removeExpiredBuckets(now);
        }
        Deque<Instant> requestTimes = attempts.computeIfAbsent(
            requestKey,
            ignored -> new ArrayDeque<>()
        );
        Instant windowStart = now.minus(properties.loginAttemptWindow());

        synchronized (requestTimes) {
            while (!requestTimes.isEmpty() && !requestTimes.peekFirst().isAfter(windowStart)) {
                requestTimes.removeFirst();
            }
            if (requestTimes.size() >= properties.loginAttemptLimit()) {
                long retryAfter = Math.max(
                    1,
                    properties.loginAttemptWindow().minus(
                        java.time.Duration.between(requestTimes.peekFirst(), now)
                    ).toSeconds()
                );
                throw new LoginRateLimitedException(retryAfter);
            }
            requestTimes.addLast(now);
        }
    }

    private void removeExpiredBuckets(Instant now) {
        Instant windowStart = now.minus(properties.loginAttemptWindow());
        attempts.forEach((key, requestTimes) -> {
            synchronized (requestTimes) {
                while (!requestTimes.isEmpty() && !requestTimes.peekFirst().isAfter(windowStart)) {
                    requestTimes.removeFirst();
                }
                if (requestTimes.isEmpty()) {
                    attempts.remove(key, requestTimes);
                }
            }
        });
    }
}
