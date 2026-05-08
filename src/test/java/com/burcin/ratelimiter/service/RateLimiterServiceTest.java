package com.burcin.ratelimiter.service;

import com.burcin.ratelimiter.model.RateLimitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private ReactiveRedisTemplate<String, Long> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, Long> valueOperations;

    @InjectMocks
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(rateLimiterService, "requestLimit", 10L);
        ReflectionTestUtils.setField(rateLimiterService, "windowMinutes", 1L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void allowsRequestWithinLimit() {
        when(valueOperations.increment("rate_limit:fixed:user1")).thenReturn(Mono.just(5L));

        StepVerifier.create(rateLimiterService.isAllowed("user1"))
                .expectNextMatches(r -> r.allowed() && r.remaining() == 5 && r.limit() == 10)
                .verifyComplete();
    }

    @Test
    void allowsRequestAtExactLimit() {
        when(valueOperations.increment("rate_limit:fixed:user1")).thenReturn(Mono.just(10L));

        StepVerifier.create(rateLimiterService.isAllowed("user1"))
                .expectNextMatches(r -> r.allowed() && r.remaining() == 0)
                .verifyComplete();
    }

    @Test
    void blocksRequestOverLimit() {
        when(valueOperations.increment("rate_limit:fixed:user1")).thenReturn(Mono.just(11L));

        StepVerifier.create(rateLimiterService.isAllowed("user1"))
                .expectNextMatches(r -> !r.allowed() && r.remaining() == 0)
                .verifyComplete();
    }

    @Test
    void setsExpiryOnFirstRequest() {
        when(valueOperations.increment(any())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(any(), any())).thenReturn(Mono.just(true));

        StepVerifier.create(rateLimiterService.isAllowed("user1"))
                .expectNextMatches(RateLimitResult::allowed)
                .verifyComplete();

        verify(redisTemplate).expire(eq("rate_limit:fixed:user1"), eq(Duration.ofMinutes(1)));
    }

    @Test
    void doesNotSetExpiryOnSubsequentRequests() {
        when(valueOperations.increment(any())).thenReturn(Mono.just(3L));

        StepVerifier.create(rateLimiterService.isAllowed("user1"))
                .expectNextMatches(RateLimitResult::allowed)
                .verifyComplete();

        verify(redisTemplate, never()).expire(any(), any());
    }

    @Test
    void isolatesCountersPerUser() {
        when(valueOperations.increment("rate_limit:fixed:userA")).thenReturn(Mono.just(1L));
        when(valueOperations.increment("rate_limit:fixed:userB")).thenReturn(Mono.just(11L));
        when(redisTemplate.expire(any(), any())).thenReturn(Mono.just(true));

        StepVerifier.create(rateLimiterService.isAllowed("userA"))
                .expectNextMatches(RateLimitResult::allowed)
                .verifyComplete();

        StepVerifier.create(rateLimiterService.isAllowed("userB"))
                .expectNextMatches(r -> !r.allowed())
                .verifyComplete();
    }
}
