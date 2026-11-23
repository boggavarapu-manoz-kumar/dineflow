-- ==============================================================================
-- Flyway Database Migration: V10__invoices_and_document_management_schema.sql
-- Description: Core schema for restaurant invoices, sequential numbering, PDF document
--              metadata, storage references, audit integrity, and permissions.
-- ==============================================================================

-- 1. Restaurant Invoices Table
CREATE TABLE IF NOT EXISTS restaurant_invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
    bill_id UUID NOT NULL REFERENCES restaurant_bills(id) ON DELETE RESTRICT,
    invoice_number VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ISSUED', -- ISSUED, CANCELLED, CORRECTED
    customer_name VARCHAR(128),
    customer_tax_id VARCHAR(64),
    customer_phone VARCHAR(32),
    restaurant_name_snapshot VARCHAR(128) NOT NULL,
    tax_registration_number VARCHAR(64),
    branch_name_snapshot VARCHAR(128),
    branch_address_snapshot VARCHAR(255),
    currency_code VARCHAR(3) NOT NULL DEFAULT 'INR',
    subtotal NUMERIC(10, 2) NOT NULL,
    discount_amount NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    taxable_amount NUMERIC(10, 2) NOT NULL,
    tax_percentage NUMERIC(5, 2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    service_charge_percentage NUMERIC(5, 2) NOT NULL DEFAULT 0.00,
    service_charge_amount NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    grand_total NUMERIC(10, 2) NOT NULL,
    paid_amount NUMERIC(10, 2) NOT NULL,
    payment_method VARCHAR(32) NOT NULL DEFAULT 'PAID_COUNTER', -- CASH, CARD, UPI, COUNTER
    payment_reference VARCHAR(128),
    notes VARCHAR(512),
    cancellation_reason VARCHAR(255),
    cancelled_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    issued_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tenant_invoice_number UNIQUE (tenant_id, invoice_number)
);

CREATE INDEX IF NOT EXISTS idx_invoices_tenant_branch ON restaurant_invoices(tenant_id, branch_id);
CREATE INDEX IF NOT EXISTS idx_invoices_bill ON restaurant_invoices(bill_id);
CREATE INDEX IF NOT EXISTS idx_invoices_status ON restaurant_invoices(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_invoices_created_at ON restaurant_invoices(tenant_id, created_at DESC);

-- 2. Document Storage References Table (Tracks PDF binaries on disk or cloud object storage)
CREATE TABLE IF NOT EXISTS financial_document_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    document_type VARCHAR(32) NOT NULL, -- INVOICE_PDF, RECEIPT_PDF
    entity_id UUID NOT NULL, -- references invoice_id
    storage_provider VARCHAR(32) NOT NULL DEFAULT 'LOCAL_FILE', -- LOCAL_FILE, S3, GCS
    storage_key VARCHAR(512) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(64) NOT NULL DEFAULT 'application/pdf',
    file_size_bytes BIGINT NOT NULL,
    checksum_sha256 VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_financial_docs_tenant_entity ON financial_document_records(tenant_id, entity_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_financial_docs_storage_key ON financial_document_records(tenant_id, storage_key);

-- 3. Register Invoice Permissions
INSERT INTO permissions (id, code, description, category) VALUES
    ('10000000-0000-0000-0000-000000000050', 'INVOICE_VIEW', 'View invoices and financial document metadata', 'INVOICING'),
    ('10000000-0000-0000-0000-000000000051', 'INVOICE_GENERATE', 'Generate fiscal invoices from paid bills', 'INVOICING'),
    ('10000000-0000-0000-0000-000000000052', 'INVOICE_CANCEL', 'Cancel or issue credit notes for issued invoices', 'INVOICING')
ON CONFLICT (code) DO NOTHING;

-- Map permissions to roles
-- HOTEL_OWNER, HOTEL_ADMIN, BRANCH_MANAGER get all invoice permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('HOTEL_OWNER', 'HOTEL_ADMIN', 'BRANCH_MANAGER')
  AND p.code IN ('INVOICE_VIEW', 'INVOICE_GENERATE', 'INVOICE_CANCEL')
ON CONFLICT DO NOTHING;

-- WAITER gets INVOICE_VIEW and INVOICE_GENERATE
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'WAITER'
  AND p.code IN ('INVOICE_VIEW', 'INVOICE_GENERATE')
ON CONFLICT DO NOTHING;
