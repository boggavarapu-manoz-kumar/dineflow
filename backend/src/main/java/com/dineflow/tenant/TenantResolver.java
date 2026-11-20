package com.dineflow.tenant;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;

/**
 * Strategy interface for resolving a tenant from an incoming HTTP request.
 * Can be implemented by subdomain resolvers, signed JWT claims, or header-based resolvers.
 */
public interface TenantResolver {

    /**
     * Attempts to resolve the tenant UUID from the request.
     *
     * @param request current HTTP servlet request
     * @return Optional containing resolved Tenant UUID, or empty if none found
     */
    Optional<UUID> resolveTenant(HttpServletRequest request);
}
