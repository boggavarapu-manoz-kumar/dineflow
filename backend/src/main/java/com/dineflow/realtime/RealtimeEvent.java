package com.dineflow.realtime;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Versioned, deduplicable real-time domain notification envelope.
 * Contains only safe operational metadata (never raw payment secrets or customer PII).
 */
public record RealtimeEvent(
        UUID eventId,
        String eventType,
        String eventVersion,
        UUID tenantId,
        UUID branchId,
        String channel, // kitchen | waiter | payments | order | session
        UUID resourceId,
        Long domainVersion,
        Map<String, Object> payload,
        Instant timestamp
) {
    public static final String CURRENT_VERSION = "1.0";

    public static RealtimeEvent of(String eventType, UUID tenantId, UUID branchId, String channel,
                                   UUID resourceId, Map<String, Object> payload) {
        return ofVersioned(eventType, tenantId, branchId, channel, resourceId, null, payload);
    }

    public static RealtimeEvent ofVersioned(String eventType, UUID tenantId, UUID branchId, String channel,
                                            UUID resourceId, Long domainVersion, Map<String, Object> payload) {
        return new RealtimeEvent(
                UUID.randomUUID(),
                eventType,
                CURRENT_VERSION,
                tenantId,
                branchId,
                channel,
                resourceId,
                domainVersion,
                payload != null ? Map.copyOf(payload) : Map.of(),
                Instant.now()
        );
    }

    public String resolveDestination() {
        if ("order".equalsIgnoreCase(channel) && resourceId != null) {
            return String.format("/topic/tenant/%s/orders/%s", tenantId, resourceId);
        }
        if ("session".equalsIgnoreCase(channel) && resourceId != null) {
            return String.format("/topic/tenant/%s/sessions/%s", tenantId, resourceId);
        }
        if (branchId != null) {
            return String.format("/topic/tenant/%s/branch/%s/%s", tenantId, branchId, channel);
        }
        return String.format("/topic/tenant/%s/%s", tenantId, channel);
    }
}
