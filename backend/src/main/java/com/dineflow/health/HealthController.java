package com.dineflow.health;

import com.dineflow.common.api.ApiResponse;
import com.dineflow.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health and telemetry endpoint.
 * Conforms to standard versioning convention: GET /api/v1/health
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final String appName;
    private final String appVersion;
    private final String environment;

    public HealthController(
            @Value("${dineflow.app.name:DineFlow}") String appName,
            @Value("${dineflow.app.version:1.0.0}") String appVersion,
            @Value("${dineflow.app.environment:dev}") String environment) {
        this.appName = appName;
        this.appVersion = appVersion;
        this.environment = environment;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<HealthResponse>> getHealth() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("database", "Configured (PostgreSQL / Flyway)");
        details.put("multiTenantEngine", "Active");
        details.put("activeTenantContext", TenantContext.getTenantId().map(Object::toString).orElse("GLOBAL"));

        HealthResponse healthData = HealthResponse.up(appName, appVersion, environment, details);
        return ResponseEntity.ok(ApiResponse.ok(healthData, "DineFlow API service is healthy"));
    }
}
