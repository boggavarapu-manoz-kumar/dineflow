package com.dineflow.payment.dto;

import java.util.Map;

public record WebhookProcessingMetrics(
        long totalReceived,
        long totalProcessed,
        long totalIgnored,
        long totalFailed,
        long pendingStalePayments,
        Map<String, Long> eventsByType
) {}
