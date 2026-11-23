package com.dineflow.security;

import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Legacy compatibility facade delegating directly to the centralized
 * {@link com.dineflow.security.ratelimit.RateLimiterService} (Redis in production, bounded memory in dev/test).
 */
@Component
public class RateLimiterService {

    private final com.dineflow.security.ratelimit.RateLimiterService delegate;

    public RateLimiterService(com.dineflow.security.ratelimit.RateLimiterService delegate) {
        this.delegate = delegate;
    }

    public boolean tryAcquire(String key, int maxTokens, long refillDurationSeconds) {
        return delegate.tryConsume(key, maxTokens, Duration.ofSeconds(Math.max(1L, refillDurationSeconds))).allowed();
    }
}
