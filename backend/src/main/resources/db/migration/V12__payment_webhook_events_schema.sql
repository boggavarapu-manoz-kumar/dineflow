-- ==============================================================================
-- Flyway Database Migration: V12__payment_webhook_events_schema.sql
-- Description: Idempotent processing ledger for incoming payment gateway webhooks.
-- ==============================================================================

CREATE TABLE IF NOT EXISTS payment_webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_name VARCHAR(32) NOT NULL,
    provider_event_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'RECEIVED', -- RECEIVED, PROCESSED, IGNORED, FAILED
    error_message VARCHAR(512),
    raw_payload TEXT,
    processed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_provider_event_id UNIQUE (provider_name, provider_event_id)
);

CREATE INDEX IF NOT EXISTS idx_webhook_provider_event ON payment_webhook_events(provider_name, provider_event_id);
CREATE INDEX IF NOT EXISTS idx_webhook_status ON payment_webhook_events(status);
