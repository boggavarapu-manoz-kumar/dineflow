package com.dineflow.payment.dto;

public record ReconciliationReport(
        int scannedCount,
        int resolvedCount,
        int discrepancyCount,
        int timedOutCount,
        String summary
) {}
