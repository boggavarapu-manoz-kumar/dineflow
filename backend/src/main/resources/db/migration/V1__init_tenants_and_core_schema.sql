-- ==============================================================================
-- Flyway Database Migration: V1__init_tenants_and_core_schema.sql
-- Description: Core schema establishing multi-tenant foundations, hotel profiles,
--              system settings, and audit logs with UUID primary keys & UTC timestamps.
-- ==============================================================================

-- Ensure pgcrypto extension for UUID generation if supported
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- -----------------------------------------------------------------------------
-- 1. Tenants Table: Represents subscribing hotels/restaurants in DineFlow SaaS
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug VARCHAR(64) NOT NULL,
    business_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    subscription_plan VARCHAR(32) NOT NULL DEFAULT 'STANDARD',
    subscription_expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tenants_slug UNIQUE (slug)
);

CREATE INDEX IF NOT EXISTS idx_tenants_status ON tenants(status);
CREATE INDEX IF NOT EXISTS idx_tenants_created_at ON tenants(created_at);

-- -----------------------------------------------------------------------------
-- 2. Hotel Profiles: Detailed settings, branding, tax, and contact details
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hotel_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    contact_email VARCHAR(255) NOT NULL,
    contact_phone VARCHAR(32),
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city VARCHAR(100),
    country VARCHAR(100) DEFAULT 'India',
    currency_code VARCHAR(3) NOT NULL DEFAULT 'INR',
    tax_percentage NUMERIC(5, 2) NOT NULL DEFAULT 5.00,
    service_charge_percentage NUMERIC(5, 2) NOT NULL DEFAULT 0.00,
    logo_url VARCHAR(1024),
    primary_color_hex VARCHAR(7) DEFAULT '#0F172A',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_hotel_profiles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT uq_hotel_profiles_tenant UNIQUE (tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_hotel_profiles_tenant_id ON hotel_profiles(tenant_id);

-- -----------------------------------------------------------------------------
-- 3. System Audit Log: Immutable record of tenant security & operational events
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    actor_id VARCHAR(128),
    actor_type VARCHAR(64) NOT NULL,
    action VARCHAR(128) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128),
    details_json TEXT,
    ip_address VARCHAR(64),
    user_agent VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_tenant_id ON audit_logs(tenant_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_action ON audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs(created_at);
