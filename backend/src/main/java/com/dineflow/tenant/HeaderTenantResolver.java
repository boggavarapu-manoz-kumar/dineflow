package com.dineflow.tenant;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Header and subdomain based Tenant Resolver.
 * Resolves tenant identity from standard header (X-Tenant-ID / X-Tenant-Slug) or Host subdomain.
 * In production, this can verify digital signatures or lookup tenant registry.
 */
@Component
public class HeaderTenantResolver implements TenantResolver {

    private static final Logger log = LoggerFactory.getLogger(HeaderTenantResolver.class);
    public static final String TENANT_HEADER = "X-Tenant-ID";

    @Override
    public Optional<UUID> resolveTenant(HttpServletRequest request) {
        String headerValue = request.getHeader(TENANT_HEADER);
        if (headerValue != null && !headerValue.isBlank()) {
            try {
                return Optional.of(UUID.fromString(headerValue.trim()));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid UUID format in {} header: {}", TENANT_HEADER, headerValue);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
