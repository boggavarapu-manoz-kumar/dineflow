package com.dineflow.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("TenantContext accurately stores, retrieves and clears tenant UUID")
    void shouldManageTenantContext() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        assertTrue(TenantContext.getTenantId().isPresent());
        assertEquals(tenantId, TenantContext.getTenantId().get());
        assertEquals(tenantId, TenantContext.requireTenantId());

        TenantContext.clear();
        assertTrue(TenantContext.getTenantId().isEmpty());
    }
}