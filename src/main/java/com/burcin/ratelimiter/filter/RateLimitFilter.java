package com.burcin.ratelimiter.filter;

import com.burcin.ratelimiter.model.RateLimitResult;
import com.burcin.ratelimiter.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Reactive WebFilter responsible for enforcing rate limiting.
 *
 * Intercepts all HTTP requests, resolves the client IP (with trusted-proxy validation),
 * delegates to RateLimiterService, and writes standard rate-limit headers on every response.
 */
@Component
@Order(-1)
@RequiredArgsConstructor
public class RateLimitFilter implements WebFilter {

    private final RateLimiterService rateLimiterService;

    @Value("${rate-limiter.trusted-proxies:127.0.0.1,0:0:0:0:0:0:0:1}")
    private String trustedProxiesConfig;

    @Value("${rate-limiter.window-minutes:1}")
    private long windowMinutes;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String clientIp = resolveClientIp(exchange.getRequest());

        return rateLimiterService.isAllowed(clientIp)
                .flatMap(result -> {
                    addRateLimitHeaders(exchange.getResponse(), result);
                    return result.allowed()
                            ? chain.filter(exchange)
                            : tooManyRequests(exchange);
                });
    }

    /**
     * Resolves the real client IP.
     *
     * X-Forwarded-For is only trusted when the direct caller is a known proxy,
     * preventing spoofing by arbitrary clients.
     */
    private String resolveClientIp(ServerHttpRequest request) {
        String remoteIp = request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : null;

        if (remoteIp != null && isTrustedProxy(remoteIp)) {
            String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                return xForwardedFor.split(",")[0].trim();
            }
        }

        return remoteIp != null ? remoteIp : "unknown";
    }

    private boolean isTrustedProxy(String ip) {
        return Arrays.stream(trustedProxiesConfig.split(","))
                .map(String::trim)
                .anyMatch(ip::equals);
    }

    private void addRateLimitHeaders(ServerHttpResponse response, RateLimitResult result) {
        var headers = response.getHeaders();
        headers.add("X-RateLimit-Limit", String.valueOf(result.limit()));
        headers.add("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        if (!result.allowed()) {
            headers.add("Retry-After", String.valueOf(windowMinutes * 60));
        }
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = "{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Please try again later.\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
