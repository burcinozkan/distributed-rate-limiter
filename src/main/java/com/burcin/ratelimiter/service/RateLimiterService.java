package com.burcin.ratelimiter.service;

import com.burcin.ratelimiter.model.RateLimitResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Service to manage rate limiting using a 'Fixed Window' counter in Redis.
 * Pros: High performance, atomic increments, very low memory usage.
 * Cons: Can allow double the limit at the edges of the window (Spike problem).
 */
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final ReactiveRedisTemplate<String, Long> redisTemplate;

    @Value("${rate-limiter.request-limit:10}")
    private long requestLimit;

    @Value("${rate-limiter.window-minutes:1}")
    private long windowMinutes;

    /**
     * Evaluates if a request from a specific user is allowed.
     * Uses Redis INCR and EXPIRE for atomic counting.
     *
     * @param userId Unique identifier for the user (e.g., IP address)
     * @return Mono<RateLimitResult> with allow decision, remaining quota, and limit
     */
    public Mono<RateLimitResult> isAllowed(String userId) {
        String key = "rate_limit:fixed:" + userId;

        return redisTemplate.opsForValue().increment(key)
                .flatMap(currentCount -> {
                    if (currentCount == 1) {
                        return redisTemplate.expire(key, Duration.ofMinutes(windowMinutes))
                                .thenReturn(buildResult(currentCount));
                    }
                    return Mono.just(buildResult(currentCount));
                });
    }

    private RateLimitResult buildResult(long currentCount) {
        boolean allowed = currentCount <= requestLimit;
        long remaining = Math.max(0, requestLimit - currentCount);
        return new RateLimitResult(allowed, remaining, requestLimit);
    }
}
