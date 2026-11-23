package com.dineflow.security.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded in-memory fixed-window rate limiter for local development, automated tests,
 * and emergency per-replica fallback when Redis is unreachable on sensitive endpoints.
 */
@Service
@ConditionalOnProperty(name = "dineflow.rate-limit.store", havingValue = "memory", matchIfMissing = true)
public class InMemoryRateLimiterService implements RateLimiterService {

    private record WindowCounter(Instant expiresAt, AtomicInteger count) {}

    private final ConcurrentHashMap<String, WindowCounter> buckets = new ConcurrentHashMap<>();
    private final int maxBuckets;
    private final MeterRegistry meterRegistry;

    @Autowired
    public InMemoryRateLimiterService(RateLimitProperties properties,
                                      @Autowired(required = false) MeterRegistry meterRegistry) {
        this.maxBuckets = Math.max(1000, properties.getMaxInMemoryBuckets());
        this.meterRegistry = meterRegistry;
    }

    public InMemoryRateLimiterService(int maxBuckets) {
        this.maxBuckets = Math.max(100, maxBuckets);
        this.meterRegistry = null;
    }

    @Override
    public RateLimitDecision tryConsume(String bucketKey, int maxRequests, Duration window) {
        return tryConsume(null, bucketKey, maxRequests, window);
    }

    @Override
    public RateLimitDecision tryConsume(RateLimitCategory category, String bucketKey, int maxRequests, Duration window) {
        Instant now = Instant.now();
        if (buckets.size() >= maxBuckets && !buckets.containsKey(bucketKey)) {
            evictExpired(now);
            if (buckets.size() >= maxBuckets) {
                buckets.clear(); // hard upper bound to guarantee O(1) bounded memory under key-flood attack
            }
        }

        WindowCounter counter = buckets.compute(bucketKey, (k, existing) -> {
            if (existing == null || !existing.expiresAt().isAfter(now)) {
                return new WindowCounter(now.plus(window), new AtomicInteger(0));
            }
            return existing;
        });

        int current = counter.count().incrementAndGet();
        boolean allowed = current <= maxRequests;
        recordMetric(category, allowed ? "allowed" : "rejected");

        if (allowed) {
            return new RateLimitDecision(true, Math.max(0, maxRequests - current), 0);
        }
        long retryAfter = Math.max(1, Duration.between(now, counter.expiresAt()).getSeconds());
        return new RateLimitDecision(false, 0, retryAfter);
    }

    public int currentBucketCount() {
        return buckets.size();
    }

    public void clearAll() {
        buckets.clear();
    }

    private void evictExpired(Instant now) {
        buckets.entrySet().removeIf(e -> !e.getValue().expiresAt().isAfter(now));
    }

    private void recordMetric(RateLimitCategory category, String outcome) {
        if (meterRegistry != null) {
            String cat = category != null ? category.getKeySegment() : "generic";
            meterRegistry.counter("dineflow.ratelimit.requests", "category", cat, "outcome", outcome, "store", "memory").increment();
        }
    }
}
