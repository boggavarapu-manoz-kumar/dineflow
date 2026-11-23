package com.dineflow.realtime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "realtime_outbox_events")
public class RealtimeOutboxEvent {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_DEAD_LETTER = "FAILED_DEAD_LETTER";

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "event_version", nullable = false, length = 16)
    private String eventVersion = RealtimeEvent.CURRENT_VERSION;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "channel", nullable = false, length = 32)
    private String channel;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "destination", nullable = false, length = 255)
    private String destination;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "status", nullable = false, length = 32)
    private String status = STATUS_PENDING;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error", length = 512)
    private String lastError;

    public RealtimeOutboxEvent() {}

    public RealtimeOutboxEvent(RealtimeEvent event, String payloadJson) {
        this.id = UUID.randomUUID();
        this.eventId = event.eventId();
        this.eventType = event.eventType();
        this.eventVersion = event.eventVersion();
        this.tenantId = event.tenantId();
        this.branchId = event.branchId();
        this.channel = event.channel();
        this.resourceId = event.resourceId();
        this.destination = event.resolveDestination();
        this.payloadJson = payloadJson;
        this.status = STATUS_PENDING;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getEventVersion() { return eventVersion; }
    public UUID getTenantId() { return tenantId; }
    public UUID getBranchId() { return branchId; }
    public String getChannel() { return channel; }
    public UUID getResourceId() { return resourceId; }
    public String getDestination() { return destination; }
    public String getPayloadJson() { return payloadJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(Instant nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
