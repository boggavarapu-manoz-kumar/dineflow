-- ==============================================================================
-- Flyway Database Migration: V21__realtime_event_outbox.sql
-- Description: Transactional outbox table for durable, at-least-once real-time
--              event publication with retry tracking and retention pruning.
-- ==============================================================================

CREATE TABLE IF NOT EXISTS realtime_outbox_events (
    id UUID NOT NULL PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(64) NOT NULL,
    event_version VARCHAR(16) NOT NULL DEFAULT '1.0',
    tenant_id UUID NOT NULL,
    branch_id UUID,
    channel VARCHAR(32) NOT NULL,
    resource_id UUID,
    destination VARCHAR(255) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(512)
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_next_retry
    ON realtime_outbox_events (status, next_retry_at, created_at);

CREATE INDEX IF NOT EXISTS idx_outbox_status_published_at
    ON realtime_outbox_events (status, published_at);
