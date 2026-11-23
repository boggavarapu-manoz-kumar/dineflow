package com.dineflow.security.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Distributed Redis/Valkey fixed-window rate limiter using an atomic Lua script.
 * Enforces endpoint-specific failure policies (FAIL_OPEN, FAIL_CLOSED, or conservative
 * LOCAL_FALLBACK) so sensitive auth/payment endpoints are never left silently unprotected
 * during a Redis outage.
 */
@Service
@ConditionalOnProperty(name = "dineflow.rate-limit.store", havingValue = "redis")
public class RedisRateLimiterService implements RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiterService.class);

    private static final String LUA_SCRIPT = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl < 0 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
                ttl = tonumber(ARGV[1])
            end
            return {current, ttl}
            """;

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;
    private final MeterRegistry meterRegistry;
    private final DefaultRedisScript<List> script;
    private final InMemoryRateLimiterService emergencyFallback;

    @Autowired
    public RedisRateLimiterService(StringRedisTemplate redisTemplate,
                                   RateLimitProperties properties,
                                   @Autowired(required = false) MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.script = new DefaultRedisScript<>(LUA_SCRIPT, List.class);
        this.emergencyFallback = new InMemoryRateLimiterService(Math.min(10_000, properties.getMaxInMemoryBuckets()));
    }

    @Override
    public RateLimitDecision tryConsume(String bucketKey, int maxRequests, Duration window) {
        return tryConsume(null, bucketKey, maxRequests, window);
    }

    @Override
    public RateLimitDecision tryConsume(RateLimitCategory category, String bucketKey, int maxRequests, Duration window) {
        String cat = category != null ? category.getKeySegment() : "generic";
        long startNanos = System.nanoTime();
        try {
            @SuppressWarnings("unchecked")
            List<Long> res = redisTemplate.execute(
                    script,
                    List.of("dineflow:rl:v1:" + bucketKey),
                    String.valueOf(window.toMillis())
            );
            recordLatency(cat, System.nanoTime() - startNanos);

            if (res == null || res.size() < 2) {
                return handleFailure(category, bucketKey, maxRequests, window, "empty_redis_response");
            }
            long current = res.get(0);
            long ttlMs = Math.max(1000L, res.get(1));
            if (current <= maxRequests) {
                recordOutcome(cat, "allowed");
                return new RateLimitDecision(true, Math.max(0, maxRequests - current), 0);
            }
            recordOutcome(cat, "rejected");
            return new RateLimitDecision(false, 0, Math.max(1, (ttlMs + 999) / 1000));
        } catch (Exception ex) {
            recordLatency(cat, System.nanoTime() - startNanos);
            if (meterRegistry != null) {
                meterRegistry.counter("dineflow.ratelimit.redis.failures", "category", cat).increment();
            }
            return handleFailure(category, bucketKey, maxRequests, window, ex.getClass().getSimpleName());
        }
    }

    private RateLimitDecision handleFailure(RateLimitCategory category, String bucketKey,
                                            int maxRequests, Duration window, String reason) {
        String cat = category != null ? category.getKeySegment() : "generic";
        RateLimitCategory.FailurePolicy policy = properties.failurePolicyFor(category);
        log.warn("Redis rate limiter unavailable for category={} policy={} reason={}", cat, policy, reason);

        return switch (policy) {
            case FAIL_OPEN -> {
                recordOutcome(cat, "fail_open");
                yield new RateLimitDecision(true, maxRequests, 0);
            }
            case FAIL_CLOSED -> {
                recordOutcome(cat, "fail_closed");
                yield new RateLimitDecision(false, 0, 5);
            }
            case LOCAL_FALLBACK -> {
                int conservativeLimit = Math.max(1, maxRequests / 2);
                RateLimitDecision fallbackDecision = emergencyFallback.tryConsume(bucketKey, conservativeLimit, window);
                recordOutcome(cat, fallbackDecision.allowed() ? "fallback_allowed" : "fallback_rejected");
                yield fallbackDecision;
            }
        };
    }

    private void recordOutcome(String category, String outcome) {
        if (meterRegistry != null) {
            meterRegistry.counter("dineflow.ratelimit.requests", "category", category, "outcome", outcome, "store", "redis").increment();
        }
    }

    private void recordLatency(String category, long elapsedNanos) {
        if (meterRegistry != null) {
            Timer.builder("dineflow.ratelimit.redis.latency")
                    .tag("category", category)
                    .register(meterRegistry)
                    .record(elapsedNanos, TimeUnit.NANOSECONDS);
        }
    }
}
