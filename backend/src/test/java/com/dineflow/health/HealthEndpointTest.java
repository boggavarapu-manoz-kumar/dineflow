package com.dineflow.health;

import com.dineflow.common.api.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthEndpointTest {

    @Test
    @DisplayName("HealthController returns HTTP 200 with structured status payload")
    void shouldReturnHealthyStatus() {
        HealthController controller = new HealthController("DineFlow", "1.0.0", "test");
        ResponseEntity<ApiResponse<HealthResponse>> response = controller.getHealth();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().success());
        assertEquals("UP", response.getBody().data().status());
        assertEquals("DineFlow", response.getBody().data().appName());
        assertEquals("test", response.getBody().data().environment());
    }
}