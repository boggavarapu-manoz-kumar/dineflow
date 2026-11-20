package com.dineflow.security.authorization;

import com.dineflow.security.DineFlowPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Reusable SpEL Authorization Evaluator for Spring Security.
 * Enables method-level validation:
 * e.g., @PreAuthorize("@tenantSecurity.hasTenantAccess(#tenantId)")
 * e.g., @PreAuthorize("@tenantSecurity.isGuestSessionOwner(#sessionId)")
 */
@Component("tenantSecurity")
public class TenantSecurityEvaluator {

    public boolean hasTenantAccess(UUID resourceTenantId) {
        if (resourceTenantId == null) {
            return false;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof DineFlowPrincipal principal)) {
            return false;
        }

        // Super Admin has global oversight access
        if ("SUPER_ADMIN".equals(principal.getRoleCode())) {
            return true;
        }

        // Strict tenant match: Principal's authenticated tenant must match the target resource's tenant
        return resourceTenantId.equals(principal.getTenantId());
    }

    public boolean isGuestSessionOwner(UUID sessionId) {
        if (sessionId == null) {
            return false;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof DineFlowPrincipal principal)) {
            return false;
        }

        if (!"GUEST".equals(principal.getType())) {
            return false;
        }

        return sessionId.equals(principal.getUserId());
    }
}