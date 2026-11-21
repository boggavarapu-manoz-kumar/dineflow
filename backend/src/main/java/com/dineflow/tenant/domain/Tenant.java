package com.dineflow.tenant.domain;

import com.dineflow.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * JPA mapping of the tenants table (V1 migration).
 * Used read-only for resolving business_name and status.
 */
@Entity
@Table(name = "tenants")
public class Tenant extends BaseEntity {

    @Column(name = "slug", nullable = false, length = 64)
    private String slug;

    @Column(name = "business_name", nullable = false, length = 128)
    private String businessName;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "subscription_plan", nullable = false, length = 32)
    private String subscriptionPlan;

    public Tenant() {
    }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSubscriptionPlan() { return subscriptionPlan; }
    public void setSubscriptionPlan(String subscriptionPlan) { this.subscriptionPlan = subscriptionPlan; }
}
