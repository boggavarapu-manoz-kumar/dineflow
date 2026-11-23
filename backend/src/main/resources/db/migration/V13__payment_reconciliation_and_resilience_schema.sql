-- ==============================================================================
-- Flyway Database Migration: V13__payment_reconciliation_and_resilience_schema.sql
-- Description: Adds retry tracking to webhook events and creates reconciliation log tables.
-- ==============================================================================

-- 1. Extend payment_webhook_events with retry tracking and reference fields
ALTER TABLE payment_webhook_events
    ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS payment_reference VARCHAR(64),
    ADD COLUMN IF NOT EXISTS provider_payment_id VARCHAR(128);

-- 2. Reconciliation Audit Records
CREATE TABLE IF NOT EXISTS payment_reconciliation_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    reconciliation_type VARCHAR(64) NOT NULL, -- STALE_PAYMENT_SCAN, GATEWAY_SYNC, MANUAL_AUDIT
    scanned_count INT NOT NULL DEFAULT 0,
    mismatch_count INT NOT NULL DEFAULT 0,
    reconciled_count INT NOT NULL DEFAULT 0,
    discrepancy_details TEXT,
    execution_status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED', -- RUNNING, COMPLETED, FAILED
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_recon_tenant ON payment_reconciliation_logs(tenant_id, started_at);
