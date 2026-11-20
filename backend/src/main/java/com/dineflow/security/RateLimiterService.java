package com.dineflow.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate Limiter Interface & In-Memory Development Fallback.
 * In a multi-instance production deployment, this is backed by Redis (e.g. Redisson / Token Bucket).
 */
@Component
public class RateLimiterService {

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key, int maxTokens, long refillDurationSeconds) {
        TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(maxTokens, refillDurationSeconds));
        return bucket.tryConsume();
    }

    private static class TokenBucket {
        private final int capacity;
        private final long refillIntervalMillis;
        private double tokens;
        private long lastRefillTimestamp;

        public TokenBucket(int capacity, long refillDurationSeconds) {
            this.capacity = capacity;
            this.refillIntervalMillis = refillDurationSeconds * 1000;
            this.tokens = capacity;
            this.lastRefillTimestamp = System.currentTimeMillis();
        }

        public synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTimestamp;
            if (elapsed > 0) {
                double added = (double) elapsed / refillIntervalMillis * capacity;
                tokens = Math.min(capacity, tokens + added);
                lastRefillTimestamp = now;
            }
        }
    }
}