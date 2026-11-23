-- ==============================================================================
-- Flyway Database Migration: V20__reconciliation_retry_and_job_observability.sql
-- Description: Adds bounded exponential-backoff retry metadata, manual-review
--              dead-letter flags, and job observability columns for distributed
--              payment & refund reconciliation.
-- ==============================================================================

ALTER TABLE payment_attempts
    ADD COLUMN IF NOT EXISTS reconciliation_retry_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE payment_attempts
    ADD COLUMN IF NOT EXISTS next_reconciliation_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE payment_attempts
    ADD COLUMN IF NOT EXISTS manual_review_required BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE restaurant_refunds
    ADD COLUMN IF NOT EXISTS reconciliation_retry_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE restaurant_refunds
    ADD COLUMN IF NOT EXISTS next_reconciliation_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE restaurant_refunds
    ADD COLUMN IF NOT EXISTS manual_review_required BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE payment_reconciliation_logs
    ADD COLUMN IF NOT EXISTS duration_ms BIGINT;

ALTER TABLE payment_reconciliation_logs
    ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE payment_reconciliation_logs
    ADD COLUMN IF NOT EXISTS triggered_by VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_payment_attempts_recon_due
    ON payment_attempts (manual_review_required, next_reconciliation_at);

CREATE INDEX IF NOT EXISTS idx_restaurant_refunds_recon_due
    ON restaurant_refunds (refund_status, manual_review_required, next_reconciliation_at);
