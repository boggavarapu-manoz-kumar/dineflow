package com.dineflow.identity.domain;

import com.dineflow.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_sessions")
public class CustomerSession extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "table_number", nullable = false)
    private String tableNumber;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "masked_mobile", nullable = false)
    private String maskedMobile;

    @Column(name = "session_token_hash", nullable = false, unique = true)
    private String sessionTokenHash;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getTableNumber() {
        return tableNumber;
    }

    public void setTableNumber(String tableNumber) {
        this.tableNumber = tableNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getMaskedMobile() {
        return maskedMobile;
    }

    public void setMaskedMobile(String maskedMobile) {
        this.maskedMobile = maskedMobile;
    }

    public String getSessionTokenHash() {
        return sessionTokenHash;
    }

    public void setSessionTokenHash(String sessionTokenHash) {
        this.sessionTokenHash = sessionTokenHash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}