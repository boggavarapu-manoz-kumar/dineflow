package com.dineflow.tenant;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Thread-local tenant context holder.
 * Stores the validated tenant ID for the current processing thread and any child worker threads.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new InheritableThreadLocal<>();

    private TenantContext() {
        // utility class
    }

    public static void setTenantId(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        CURRENT_TENANT.set(tenantId);
    }

    public static Optional<UUID> getTenantId() {
        return Optional.ofNullable(CURRENT_TENANT.get());
    }

    public static UUID requireTenantId() {
        return getTenantId().orElseThrow(() ->
                new IllegalStateException("Tenant context is required but not established for current thread"));
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
