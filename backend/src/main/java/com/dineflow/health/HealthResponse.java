package com.dineflow.health;

import java.time.Instant;
import java.util.Map;

/**
 * Structured DTO for system health and operational readiness response.
 */
public record HealthResponse(
        String status,
        String appName,
        String version,
        String environment,
        Instant timestamp,
        Map<String, Object> details
) {
    public static HealthResponse up(String appName, String version, String environment, Map<String, Object> details) {
        return new HealthResponse("UP", appName, version, environment, Instant.now(), details);
    }
}
