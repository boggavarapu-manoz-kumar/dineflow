-- ==============================================================================
-- Flyway Database Migration: V16__financial_reporting_indexes_and_permissions.sql
-- Description: Performance indexes for financial aggregation queries and
--              RBAC permissions for financial reporting and data exports.
-- ==============================================================================

-- 1. Financial Reporting Composite Indexes
CREATE INDEX IF NOT EXISTS idx_bills_tenant_issued_status
    ON restaurant_bills(tenant_id, issued_at, status);

CREATE INDEX IF NOT EXISTS idx_bills_tenant_branch_issued
    ON restaurant_bills(tenant_id, branch_id, issued_at);

CREATE INDEX IF NOT EXISTS idx_payments_tenant_settled_status
    ON restaurant_payments(tenant_id, settled_at, payment_status);

CREATE INDEX IF NOT EXISTS idx_payments_tenant_branch_settled
    ON restaurant_payments(tenant_id, branch_id, settled_at);

CREATE INDEX IF NOT EXISTS idx_refunds_tenant_settled_status
    ON restaurant_refunds(tenant_id, settled_at, refund_status);

CREATE INDEX IF NOT EXISTS idx_orders_tenant_created_status
    ON restaurant_orders(tenant_id, created_at, status);

-- 2. Register Financial Reporting Permissions
INSERT INTO permissions (id, code, description, category) VALUES
    ('10000000-0000-0000-0000-000000000080', 'REPORT_FINANCIAL_VIEW', 'View financial summaries, sales trends, and branch revenue analytics', 'REPORTING'),
    ('10000000-0000-0000-0000-000000000081', 'REPORT_FINANCIAL_EXPORT', 'Export CSV and PDF financial reports', 'REPORTING')
ON CONFLICT (code) DO NOTHING;

-- Map strictly to HOTEL_OWNER, HOTEL_ADMIN, and BRANCH_MANAGER (not Waiters or Kitchen Staff)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('HOTEL_OWNER', 'HOTEL_ADMIN', 'BRANCH_MANAGER')
  AND p.code IN ('REPORT_FINANCIAL_VIEW', 'REPORT_FINANCIAL_EXPORT')
ON CONFLICT DO NOTHING;
