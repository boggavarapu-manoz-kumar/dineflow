package com.dineflow.security.ratelimit;

import java.time.Duration;

public interface RateLimiterService {

    record RateLimitDecision(boolean allowed, long remaining, long retryAfterSeconds) {}

    RateLimitDecision tryConsume(String bucketKey, int maxRequests, Duration window);

    default RateLimitDecision tryConsume(RateLimitCategory category, String bucketKey, int maxRequests, Duration window) {
        return tryConsume(bucketKey, maxRequests, window);
    }
}
