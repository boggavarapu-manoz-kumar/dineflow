package com.dineflow.tenant.domain;

import com.dineflow.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * JPA mapping of the hotel_profiles table (created in V1 migration).
 * Provides per-tenant branding, currency, and tax configuration.
 * This entity is read-only from Sprint 3 — write operations are out of scope.
 */
@Entity
@Table(name = "hotel_profiles")
public class HotelProfile extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Column(name = "tax_percentage", precision = 5, scale = 2)
    private BigDecimal taxPercentage;

    @Column(name = "service_charge_percentage", precision = 5, scale = 2)
    private BigDecimal serviceChargePercentage;

    @Column(name = "logo_url", length = 1024)
    private String logoUrl;

    @Column(name = "primary_color_hex", length = 7)
    private String primaryColorHex;

    public HotelProfile() {
    }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }

    public BigDecimal getTaxPercentage() { return taxPercentage; }
    public void setTaxPercentage(BigDecimal taxPercentage) { this.taxPercentage = taxPercentage; }

    public BigDecimal getServiceChargePercentage() { return serviceChargePercentage; }
    public void setServiceChargePercentage(BigDecimal v) { this.serviceChargePercentage = v; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public String getPrimaryColorHex() { return primaryColorHex; }
    public void setPrimaryColorHex(String primaryColorHex) { this.primaryColorHex = primaryColorHex; }
}
