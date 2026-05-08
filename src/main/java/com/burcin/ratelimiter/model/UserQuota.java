package com.burcin.ratelimiter.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Data model representing a user's token bucket state.
 * This class is stored in Redis to track rate limiting metadata for each user.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserQuota implements Serializable {

    /**
     * The current number of tokens available in the user's bucket.
     * Each request consumes 1 token. If it reaches 0, the request is rate-limited.
     */
    private long remainingTokens;

    /**
     * The timestamp (in epoch milliseconds) of the last token refill.
     * This is used to calculate how many new tokens should be added based
     * on the time elapsed since the last request.
     */
    private long lastRefillTime;
}