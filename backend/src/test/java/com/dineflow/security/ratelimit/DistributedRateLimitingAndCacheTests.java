package com.dineflow.security.ratelimit;

import com.dineflow.config.ProductionConfigurationValidator;
import com.dineflow.menu.service.MenuProjectionCacheService;
import com.dineflow.security.DineFlowPrincipal;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class DistributedRateLimitingAndCacheTests {

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @Autowired
    private MenuProjectionCacheService menuCacheService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void trustedProxyValidationBlocksSpoofedForwardedForHeaders() {
        // Untrusted external peer trying to spoof X-Forwarded-For
        MockHttpServletRequest spoofedReq = new MockHttpServletRequest();
        spoofedReq.setRemoteAddr("203.0.113.55");
        spoofedReq.addHeader("X-Forwarded-For", "1.2.3.4");
        assertEquals("203.0.113.55", rateLimitingFilter.resolveClientIp(spoofedReq));

        // Trusted reverse proxy (Nginx on private network) forwarding real client IP
        MockHttpServletRequest proxiedReq = new MockHttpServletRequest();
        proxiedReq.setRemoteAddr("10.0.1.15");
        proxiedReq.addHeader("X-Forwarded-For", "198.51.100.77, 10.0.1.15");
        assertEquals("198.51.100.77", rateLimitingFilter.resolveClientIp(proxiedReq));
    }

    @Test
    void tenantBranchSeparationAndPiiFreeHashedKeys() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID branch1 = UUID.randomUUID();
        UUID branch2 = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("198.51.100.99");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new DineFlowPrincipal(userId, tenantA, branch1, "CASHIER", List.of("PAYMENT_PROCESS"), "STAFF"), null));
        String keyTenantABranch1 = rateLimitingFilter.buildSafeBucketKey(RateLimitCategory.PAYMENT_INITIATE, req);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new DineFlowPrincipal(userId, tenantA, branch2, "CASHIER", List.of("PAYMENT_PROCESS"), "STAFF"), null));
        String keyTenantABranch2 = rateLimitingFilter.buildSafeBucketKey(RateLimitCategory.PAYMENT_INITIATE, req);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new DineFlowPrincipal(userId, tenantB, branch1, "CASHIER", List.of("PAYMENT_PROCESS"), "STAFF"), null));
        String keyTenantBBranch1 = rateLimitingFilter.buildSafeBucketKey(RateLimitCategory.PAYMENT_INITIATE, req);

        assertNotEquals(keyTenantABranch1, keyTenantABranch2);
        assertNotEquals(keyTenantABranch1, keyTenantBBranch1);
        // Must NOT contain raw IP, tenant UUID, or user UUID
        assertFalse(keyTenantABranch1.contains(tenantA.toString()));
        assertFalse(keyTenantABranch1.contains(userId.toString()));
        assertFalse(keyTenantABranch1.contains("198.51.100.99"));
        assertTrue(keyTenantABranch1.startsWith("payment-initiate:"));
    }

    @Test
    void multipleReplicasShareDistributedCountersAndHandleRedisFailureAndRecovery() {
        Map<String, AtomicLong> sharedRedisState = new ConcurrentHashMap<>();
        AtomicInteger simulateOutage = new AtomicInteger(0);

        StringRedisTemplate sharedMockRedis = mock(StringRedisTemplate.class);
        when(sharedMockRedis.execute(any(RedisScript.class), anyList(), any())).thenAnswer(inv -> {
            if (simulateOutage.get() == 1) {
                throw new RedisConnectionFailureException("Simulated Redis outage");
            }
            @SuppressWarnings("unchecked")
            List<String> keys = inv.getArgument(1);
            long count = sharedRedisState.computeIfAbsent(keys.get(0), k -> new AtomicLong(0)).incrementAndGet();
            return List.of(count, 60000L);
        });

        RateLimitProperties props = new RateLimitProperties();
        props.setStore("redis");
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();

        // Two independent application replicas backed by the same Redis store
        RedisRateLimiterService replicaA = new RedisRateLimiterService(sharedMockRedis, props, metrics);
        RedisRateLimiterService replicaB = new RedisRateLimiterService(sharedMockRedis, props, metrics);

        // Limit = 4 across BOTH replicas combined
        assertTrue(replicaA.tryConsume(RateLimitCategory.PAYMENT_INITIATE, "shared-key", 4, Duration.ofMinutes(1)).allowed());
        assertTrue(replicaB.tryConsume(RateLimitCategory.PAYMENT_INITIATE, "shared-key", 4, Duration.ofMinutes(1)).allowed());
        assertTrue(replicaA.tryConsume(RateLimitCategory.PAYMENT_INITIATE, "shared-key", 4, Duration.ofMinutes(1)).allowed());
        assertTrue(replicaB.tryConsume(RateLimitCategory.PAYMENT_INITIATE, "shared-key", 4, Duration.ofMinutes(1)).allowed());
        // 5th request on replica B must be rejected with Retry-After > 0
        RateLimiterService.RateLimitDecision fifth = replicaB.tryConsume(RateLimitCategory.PAYMENT_INITIATE, "shared-key", 4, Duration.ofMinutes(1));
        assertFalse(fifth.allowed());
        assertTrue(fifth.retryAfterSeconds() >= 1);

        // Simulate Redis outage:
        simulateOutage.set(1);
        // 1. WEBHOOK / PUBLIC_MENU fail open so legitimate Razorpay retries aren't blocked
        assertTrue(replicaA.tryConsume(RateLimitCategory.WEBHOOK, "wh-retry", 300, Duration.ofMinutes(1)).allowed());
        assertTrue(replicaA.tryConsume(RateLimitCategory.PUBLIC_MENU, "menu-read", 120, Duration.ofMinutes(1)).allowed());

        // 2. AUTH_LOGIN uses conservative LOCAL_FALLBACK (max/2 = 2) so brute force is still blocked
        assertTrue(replicaA.tryConsume(RateLimitCategory.AUTH_LOGIN, "auth-outage", 4, Duration.ofMinutes(1)).allowed());
        assertTrue(replicaA.tryConsume(RateLimitCategory.AUTH_LOGIN, "auth-outage", 4, Duration.ofMinutes(1)).allowed());
        assertFalse(replicaA.tryConsume(RateLimitCategory.AUTH_LOGIN, "auth-outage", 4, Duration.ofMinutes(1)).allowed());

        // 3. Strict fail-closed mode rejects sensitive endpoints immediately on Redis outage
        props.setStrictFailClosedOnSensitive(true);
        assertFalse(replicaA.tryConsume(RateLimitCategory.PAYMENT_INITIATE, "pay-strict", 20, Duration.ofMinutes(1)).allowed());

        // Simulate Redis recovery
        simulateOutage.set(0);
        assertTrue(replicaA.tryConsume(RateLimitCategory.PAYMENT_INITIATE, "pay-recovered", 20, Duration.ofMinutes(1)).allowed());
        assertTrue(metrics.counter("dineflow.ratelimit.redis.failures", "category", "auth-login").count() >= 1.0);
    }

    @Test
    void windowExpirationConcurrentRequestsAndBoundedMemoryUnderHighKeyVolume() throws Exception {
        InMemoryRateLimiterService limiter = new InMemoryRateLimiterService(200);

        // Concurrent requests against a single bucket (limit = 5, 10 concurrent threads)
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Future<Boolean>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 10; i++) {
                futures.add(pool.submit(() ->
                        limiter.tryConsume(RateLimitCategory.COUPON_REDEEM, "concurrent-bucket", 5, Duration.ofSeconds(10)).allowed()));
            }
            long allowedCount = 0;
            for (Future<Boolean> f : futures) {
                if (f.get()) allowedCount++;
            }
            assertEquals(5, allowedCount, "Exactly 5 of 10 concurrent requests should be allowed");
        } finally {
            pool.shutdownNow();
        }

        // Window expiration check
        assertTrue(limiter.tryConsume("short-win", 1, Duration.ofMillis(100)).allowed());
        assertFalse(limiter.tryConsume("short-win", 1, Duration.ofMillis(100)).allowed());
        Thread.sleep(130);
        assertTrue(limiter.tryConsume("short-win", 1, Duration.ofMillis(100)).allowed());

        // High-volume key creation stays strictly bounded <= maxBuckets (200)
        for (int i = 0; i < 450; i++) {
            limiter.tryConsume("flood-key-" + i, 5, Duration.ofMinutes(1));
        }
        assertTrue(limiter.currentBucketCount() <= 200, "In-memory bucket map must stay strictly bounded");
    }

    @Test
    void menuProjectionCacheIsolatesTenantBranchAndInvalidatesOnUpdate() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID branch1 = UUID.randomUUID();
        UUID branch2 = UUID.randomUUID();

        AtomicInteger dbLoads = new AtomicInteger(0);

        // First load -> MISS
        MenuProjectionCacheService.CachedMenuProjection first = menuCacheService.getOrLoad(tenantA, branch1, () -> {
            dbLoads.incrementAndGet();
            return new MenuProjectionCacheService.CachedMenuProjection(
                    tenantA, "Hotel A", "INR", BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        });
        // Second load same tenant + branch -> HIT (dbLoads stays 1)
        MenuProjectionCacheService.CachedMenuProjection second = menuCacheService.getOrLoad(tenantA, branch1, () -> {
            dbLoads.incrementAndGet();
            return new MenuProjectionCacheService.CachedMenuProjection(
                    tenantA, "Hotel A Updated", "INR", BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        });
        assertEquals(1, dbLoads.get());
        assertEquals("Hotel A", first.restaurantName());
        assertEquals("Hotel A", second.restaurantName());

        // Different branch -> separate cache entry (MISS -> dbLoads becomes 2)
        menuCacheService.getOrLoad(tenantA, branch2, () -> {
            dbLoads.incrementAndGet();
            return new MenuProjectionCacheService.CachedMenuProjection(
                    tenantA, "Hotel A Branch 2", "INR", BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        });
        assertEquals(2, dbLoads.get());

        // Different tenant -> separate cache entry (MISS -> dbLoads becomes 3)
        menuCacheService.getOrLoad(tenantB, branch1, () -> {
            dbLoads.incrementAndGet();
            return new MenuProjectionCacheService.CachedMenuProjection(
                    tenantB, "Hotel B", "INR", BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        });
        assertEquals(3, dbLoads.get());

        // Menu update invalidates tenantA across ALL branches, but leaves tenantB cached!
        menuCacheService.evictTenantMenu(tenantA);
        MenuProjectionCacheService.CachedMenuProjection reloadedA = menuCacheService.getOrLoad(tenantA, branch1, () -> {
            dbLoads.incrementAndGet();
            return new MenuProjectionCacheService.CachedMenuProjection(
                    tenantA, "Hotel A Fresh", "INR", BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        });
        assertEquals("Hotel A Fresh", reloadedA.restaurantName());
        assertEquals(4, dbLoads.get());

        // Tenant B still cached (dbLoads stays 4)
        menuCacheService.getOrLoad(tenantB, branch1, () -> {
            dbLoads.incrementAndGet();
            return new MenuProjectionCacheService.CachedMenuProjection(
                    tenantB, "Hotel B Should Not Load", "INR", BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        });
        assertEquals(4, dbLoads.get());
    }

    @Test
    void productionValidatorRequiresRedisRateLimitStore() {
        RateLimitProperties memProps = new RateLimitProperties();
        memProps.setStore("memory");
        assertThrows(IllegalStateException.class, () -> ProductionConfigurationValidator.validateRateLimit(memProps));

        RateLimitProperties redisProps = new RateLimitProperties();
        redisProps.setStore("redis");
        redisProps.setEnabled(true);
        ProductionConfigurationValidator.validateRateLimit(redisProps);
    }

    /**
     * Real Redis/Valkey integration test. Automatically runs whenever a local or CI Redis/Valkey
     * instance is reachable at REDIS_TEST_HOST:REDIS_TEST_PORT (defaults to localhost:6379),
     * and skips cleanly on offline developer workstations without Redis running.
     */
    @Test
    void realRedisIntegrationVerifiesAtomicLuaAcrossTwoInstances() {
        String host = System.getenv().getOrDefault("REDIS_TEST_HOST", "127.0.0.1");
        int port = Integer.parseInt(System.getenv().getOrDefault("REDIS_TEST_PORT", "6379"));
        boolean redisReachable = isTcpReachable(host, port);
        Assumptions.assumeTrue(redisReachable, "Skipping live Redis integration test (no Redis/Valkey listening on " + host + ":" + port + ")");

        LettuceConnectionFactory factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port));
        factory.afterPropertiesSet();
        try {
            StringRedisTemplate template = new StringRedisTemplate(factory);
            template.afterPropertiesSet();

            RateLimitProperties props = new RateLimitProperties();
            props.setStore("redis");
            RedisRateLimiterService node1 = new RedisRateLimiterService(template, props, new SimpleMeterRegistry());
            RedisRateLimiterService node2 = new RedisRateLimiterService(template, props, new SimpleMeterRegistry());

            String key = "live-test-" + UUID.randomUUID();
            assertTrue(node1.tryConsume(RateLimitCategory.AUTH_LOGIN, key, 2, Duration.ofSeconds(5)).allowed());
            assertTrue(node2.tryConsume(RateLimitCategory.AUTH_LOGIN, key, 2, Duration.ofSeconds(5)).allowed());
            assertFalse(node1.tryConsume(RateLimitCategory.AUTH_LOGIN, key, 2, Duration.ofSeconds(5)).allowed());
        } finally {
            factory.destroy();
        }
    }

    private boolean isTcpReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), 300);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
