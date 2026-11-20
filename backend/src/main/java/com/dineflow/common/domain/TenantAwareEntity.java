package com.dineflow.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;

/**
 * Mapped superclass for every database entity belonging to a specific restaurant/hotel tenant.
 * Guarantees a tenant_id column and tenant association integrity.
 */
@MappedSuperclass
public abstract class TenantAwareEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }
}
