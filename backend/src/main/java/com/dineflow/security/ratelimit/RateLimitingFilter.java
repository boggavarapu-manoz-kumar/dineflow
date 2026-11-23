package com.dineflow.security.ratelimit;

import com.dineflow.security.DineFlowPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

/**
 * Enforces distributed (Redis/Valkey) or bounded in-memory rate limits across all
 * sensitive, financial, public, and webhook endpoints.
 * Uses hashed multi-dimensional keys (no PII in Redis) and validates trusted proxies
 * before accepting X-Forwarded-For client addresses.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);

    private final RateLimiterService rateLimiter;
    private final RateLimitProperties properties;

    public RateLimitingFilter(RateLimiterService rateLimiter, RateLimitProperties properties) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitCategory category = resolveCategoryForPath(request.getRequestURI());
        if (category == null) {
            filterChain.doFilter(request, response);
            return;
        }

        int limit = properties.limitFor(category);
        String bucketKey = buildSafeBucketKey(category, request);

        RateLimiterService.RateLimitDecision decision = rateLimiter.tryConsume(category, bucketKey, limit, ONE_MINUTE);
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));

        if (!decision.allowed()) {
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"Too many requests. Please try again later.\",\"errorCode\":\"RATE_LIMIT_EXCEEDED\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    public RateLimitCategory resolveCategoryForPath(String path) {
        if (path == null) return null;
        if (path.startsWith("/api/v1/auth/login")
                || path.startsWith("/api/v1/auth/refresh")
                || path.startsWith("/api/v1/auth/password/")
                || path.startsWith("/api/v1/auth/password-reset")) {
            return RateLimitCategory.AUTH_LOGIN;
        }
        if (path.startsWith("/api/v1/customer-sessions")) {
            return RateLimitCategory.GUEST_SESSION;
        }
        if (path.startsWith("/api/v1/public/qr") || path.startsWith("/api/v1/tables/entry")) {
            return RateLimitCategory.QR_VALIDATION;
        }
        if (path.startsWith("/api/v1/payments/refunds") || path.startsWith("/api/v1/refunds")) {
            return RateLimitCategory.REFUND_REQUEST;
        }
        if (path.startsWith("/api/v1/payments/initiate")) {
            return RateLimitCategory.PAYMENT_INITIATE;
        }
        if (path.contains("/promotions/bills/") || path.contains("/promotions/validate")) {
            return RateLimitCategory.COUPON_REDEEM;
        }
        if (path.startsWith("/api/v1/customer/menu")) {
            return RateLimitCategory.PUBLIC_MENU;
        }
        if (path.startsWith("/api/v1/public/images/") || (path.contains("/invoices/") && path.endsWith("/pdf"))) {
            return RateLimitCategory.MEDIA_DOCUMENT;
        }
        if (path.startsWith("/api/v1/payments/webhook/")) {
            return RateLimitCategory.WEBHOOK;
        }
        return null;
    }

    /**
     * Builds a bounded, PII-free, tenant- and branch-aware bucket key.
     */
    public String buildSafeBucketKey(RateLimitCategory category, HttpServletRequest request) {
        String tenantPart = "public";
        String branchPart = "all";
        String actorPart = resolveClientIp(request);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof DineFlowPrincipal principal) {
            if (principal.getTenantId() != null) {
                tenantPart = principal.getTenantId().toString();
            }
            if (principal.getBranchId() != null) {
                branchPart = principal.getBranchId().toString();
            }
            if (principal.getUserId() != null) {
                actorPart = principal.getType() + ":" + principal.getUserId();
            }
        }

        String rawDimension = tenantPart + "|" + branchPart + "|" + actorPart;
        return category.getKeySegment() + ":" + shortSha256(rawDimension);
    }

    /**
     * Only trusts X-Forwarded-For when the immediate TCP peer (remoteAddr) is a configured trusted proxy.
     * Walks the forwarded chain from right to left, skipping trusted proxies, so an attacker cannot
     * spoof the leftmost hop to bypass IP-based rate limiting.
     */
    public String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr() != null ? request.getRemoteAddr().trim() : "unknown";
        if (isTrustedProxy(remoteAddr)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String[] hops = forwarded.split(",");
                for (int i = hops.length - 1; i >= 0; i--) {
                    String hop = hops[i].trim();
                    if (!hop.isBlank() && !isTrustedProxy(hop)) {
                        return hop;
                    }
                }
                // If every hop in the header is a trusted proxy, fall back to leftmost non-blank hop
                String firstHop = hops[0].trim();
                if (!firstHop.isBlank()) {
                    return firstHop;
                }
            }
        }
        return remoteAddr;
    }

    private boolean isTrustedProxy(String remoteAddr) {
        List<String> trusted = properties.getTrustedProxies();
        if (trusted == null || trusted.isEmpty()) {
            return false;
        }
        for (String entry : trusted) {
            if (entry == null || entry.isBlank()) continue;
            if (remoteAddr.equals(entry) || remoteAddr.startsWith(entry)) {
                return true;
            }
        }
        return false;
    }

    private static String shortSha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
