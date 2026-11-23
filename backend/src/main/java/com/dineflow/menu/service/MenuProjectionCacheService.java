package com.dineflow.menu.service;

import com.dineflow.menu.dto.CustomerMenuResponse;
import com.dineflow.security.ratelimit.RateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Tenant- and branch-aware cache for public menu catalog projections.
 * Uses O(1) per-tenant cache versioning so any category/item/override update
 * invalidates all branches of that tenant immediately across all replicas without
 * Redis KEYS scans. Never caches guest PII, payment state, or financial balances.
 */
@Service
public class MenuProjectionCacheService {

    private static final Logger log = LoggerFactory.getLogger(MenuProjectionCacheService.class);
    private static final String VERSION_KEY_PREFIX = "dineflow:cache:v1:menu:ver:";
    private static final String DATA_KEY_PREFIX = "dineflow:cache:v1:menu:data:";
    private static final int MAX_LOCAL_ENTRIES = 5_000;

    public record CachedMenuProjection(
            UUID tenantId,
            String restaurantName,
            String currencyCode,
            BigDecimal taxPercentage,
            BigDecimal serviceChargePercentage,
            List<CustomerMenuResponse.CustomerCategoryResponse> categories
    ) {}

    private record LocalCacheEntry(CachedMenuProjection value, Instant expiresAt) {}

    private final boolean useRedis;
    private final Duration menuTtl;
    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    // Local bounded fallback / dev-test store
    private final ConcurrentHashMap<UUID, AtomicLong> localVersions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalCacheEntry> localEntries = new ConcurrentHashMap<>();
    // Per-key single-flight lock to prevent thundering herd / cache stampede
    private final ConcurrentHashMap<String, Object> stampedeLocks = new ConcurrentHashMap<>();

    @Autowired
    public MenuProjectionCacheService(RateLimitProperties rateLimitProperties,
                                      @Autowired(required = false) StringRedisTemplate redisTemplate,
                                      @Autowired(required = false) MeterRegistry meterRegistry,
                                      @Value("${dineflow.cache.menu-ttl-seconds:120}") long menuTtlSeconds) {
        this.useRedis = "redis".equalsIgnoreCase(rateLimitProperties.getStore()) && redisTemplate != null;
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
        this.menuTtl = Duration.ofSeconds(Math.max(10L, menuTtlSeconds));
        this.objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }

    public CachedMenuProjection getOrLoad(UUID tenantId, UUID branchId, Supplier<CachedMenuProjection> loader) {
        long version = currentVersion(tenantId);
        String branchSegment = branchId != null ? branchId.toString() : "default";
        String dataKey = DATA_KEY_PREFIX + tenantId + ":" + branchSegment + ":v" + version;

        CachedMenuProjection hit = readFromCache(dataKey);
        if (hit != null) {
            recordCacheMetric("hit");
            return hit;
        }

        // Stampede protection: serialize concurrent misses on the same tenant/branch key
        Object lock = stampedeLocks.computeIfAbsent(dataKey, k -> new Object());
        try {
            synchronized (lock) {
                CachedMenuProjection doubleCheck = readFromCache(dataKey);
                if (doubleCheck != null) {
                    recordCacheMetric("hit");
                    return doubleCheck;
                }
                recordCacheMetric("miss");
                CachedMenuProjection loaded = loader.get();
                if (loaded != null) {
                    writeToCache(dataKey, loaded);
                }
                return loaded;
            }
        } finally {
            stampedeLocks.remove(dataKey, lock);
        }
    }

    /**
     * O(1) invalidation for all branches of a tenant by bumping its cache version.
     */
    public void evictTenantMenu(UUID tenantId) {
        if (tenantId == null) return;
        if (meterRegistry != null) {
            meterRegistry.counter("dineflow.cache.invalidations", "cache", "public_menu").increment();
        }
        if (useRedis) {
            try {
                redisTemplate.opsForValue().increment(VERSION_KEY_PREFIX + tenantId);
                return;
            } catch (Exception ex) {
                log.warn("Redis cache invalidation error for tenant={}: {}", tenantId, ex.getClass().getSimpleName());
            }
        }
        localVersions.computeIfAbsent(tenantId, k -> new AtomicLong(1)).incrementAndGet();
        localEntries.keySet().removeIf(k -> k.startsWith(DATA_KEY_PREFIX + tenantId + ":"));
    }

    private long currentVersion(UUID tenantId) {
        if (useRedis) {
            try {
                String raw = redisTemplate.opsForValue().get(VERSION_KEY_PREFIX + tenantId);
                return raw != null ? Long.parseLong(raw) : 1L;
            } catch (Exception ex) {
                log.warn("Redis cache version read error for tenant={}: {}", tenantId, ex.getClass().getSimpleName());
                recordCacheMetric("error");
            }
        }
        return localVersions.computeIfAbsent(tenantId, k -> new AtomicLong(1)).get();
    }

    private CachedMenuProjection readFromCache(String dataKey) {
        if (useRedis) {
            try {
                String json = redisTemplate.opsForValue().get(dataKey);
                if (json != null && !json.isBlank()) {
                    return objectMapper.readValue(json, CachedMenuProjection.class);
                }
                return null;
            } catch (Exception ex) {
                log.warn("Redis menu cache read failed for key={}: {}", dataKey, ex.getClass().getSimpleName());
                recordCacheMetric("error");
                return null;
            }
        }

        LocalCacheEntry entry = localEntries.get(dataKey);
        if (entry == null) return null;
        if (entry.expiresAt().isBefore(Instant.now())) {
            localEntries.remove(dataKey);
            return null;
        }
        return entry.value();
    }

    private void writeToCache(String dataKey, CachedMenuProjection projection) {
        if (useRedis) {
            try {
                String json = objectMapper.writeValueAsString(projection);
                redisTemplate.opsForValue().set(dataKey, json, menuTtl);
                return;
            } catch (Exception ex) {
                log.warn("Redis menu cache write failed for key={}: {}", dataKey, ex.getClass().getSimpleName());
                recordCacheMetric("error");
                return;
            }
        }

        if (localEntries.size() >= MAX_LOCAL_ENTRIES) {
            Instant now = Instant.now();
            localEntries.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
            if (localEntries.size() >= MAX_LOCAL_ENTRIES) {
                localEntries.clear();
            }
        }
        localEntries.put(dataKey, new LocalCacheEntry(projection, Instant.now().plus(menuTtl)));
    }

    private void recordCacheMetric(String outcome) {
        if (meterRegistry != null) {
            meterRegistry.counter("dineflow.cache.requests", "cache", "public_menu", "outcome", outcome).increment();
        }
    }
}
