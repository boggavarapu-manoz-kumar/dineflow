-- ==============================================================================
-- Flyway Database Migration: V19__payment_idempotency_and_reliability.sql
-- Description: Adds request payload hash and ambiguous-outcome tracking to
--              payment_attempts for idempotency conflict detection and safe
--              reconciliation of timed-out / ambiguous provider interactions.
-- ==============================================================================

ALTER TABLE payment_attempts
    ADD COLUMN IF NOT EXISTS request_hash VARCHAR(64);

ALTER TABLE payment_attempts
    ADD COLUMN IF NOT EXISTS ambiguous_outcome BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_payment_attempts_provider_order_id
    ON payment_attempts (provider_order_id);

CREATE INDEX IF NOT EXISTS idx_restaurant_payments_status_created
    ON restaurant_payments (tenant_id, payment_status, created_at);
