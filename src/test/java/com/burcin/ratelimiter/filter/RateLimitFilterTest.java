package com.burcin.ratelimiter.filter;


import com.burcin.ratelimiter.model.RateLimitResult;
import com.burcin.ratelimiter.service.RateLimiterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest
@Import(RateLimitFilter.class)
class RateLimitFilterTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private RateLimiterService rateLimiterService;

    @Test
    void returnsOkWithRateLimitHeadersWhenAllowed() {
        when(rateLimiterService.isAllowed(any()))
                .thenReturn(Mono.just(new RateLimitResult(true, 7, 10)));

        webTestClient.get().uri("/api/test")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-RateLimit-Limit", "10")
                .expectHeader().valueEquals("X-RateLimit-Remaining", "7")
                .expectHeader().doesNotExist("Retry-After");
    }

    @Test
    void returns429WithHeadersAndBodyWhenBlocked() {
        when(rateLimiterService.isAllowed(any()))
                .thenReturn(Mono.just(new RateLimitResult(false, 0, 10)));

        webTestClient.get().uri("/api/test")
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().valueEquals("X-RateLimit-Limit", "10")
                .expectHeader().valueEquals("X-RateLimit-Remaining", "0")
                .expectHeader().valueEquals("Retry-After", "60")
                .expectHeader().contentType("application/json")
                .expectBody()
                .jsonPath("$.error").isEqualTo("Too Many Requests");
    }

    @Test
    void remainingIsZeroAtExactLimit() {
        when(rateLimiterService.isAllowed(any()))
                .thenReturn(Mono.just(new RateLimitResult(true, 0, 10)));

        webTestClient.get().uri("/api/test")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-RateLimit-Remaining", "0");
    }
}
