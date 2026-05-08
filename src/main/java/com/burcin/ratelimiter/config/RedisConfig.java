package com.burcin.ratelimiter.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.GenericToStringSerializer;

@Configuration
public class RedisConfig {

    /**
     * Defines a ReactiveRedisTemplate bean for distributed rate limiting.
     *
     * Key type  : String  → Redis keys will be stored as plain strings.
     * Value type: Long    → Counter values will be stored as numeric values.
     *
     * ReactiveRedisTemplate is used because:
     * - It supports non-blocking I/O
     * - It integrates with Spring WebFlux
     * - It provides better scalability under high load
     */

    @Bean
    public ReactiveRedisTemplate<String, Long> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory factory) {

        /*
         * Redis stores data as byte arrays internally.
         * Therefore, we must define how keys and values are serialized/deserialized.
         */

        RedisSerializationContext<String, Long> context =
                RedisSerializationContext
                        // Serialize Redis keys as plain strings
                        .<String, Long>newSerializationContext(RedisSerializer.string())
                        /*
                         * Serialize Long values using GenericToStringSerializer.
                         * Redis natively works with strings, so Long values are converted
                         * to String format during storage and converted back to Long when read.
                         *
                         * This approach ensures:
                         * - Compatibility with INCR operations
                         * - Minimal memory overhead
                         * - No unnecessary JSON serialization
                         */
                        .value(new GenericToStringSerializer<>(Long.class))
                        .build();

        /*
         * Create the ReactiveRedisTemplate using:
         * - The provided connection factory (Lettuce under the hood)
         * - The defined serialization context
         */
        return new ReactiveRedisTemplate<>(factory, context);
    }
}