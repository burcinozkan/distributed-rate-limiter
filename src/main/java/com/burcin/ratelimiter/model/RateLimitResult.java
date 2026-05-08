package com.burcin.ratelimiter.model;

public record RateLimitResult(boolean allowed, long remaining, long limit) {}
