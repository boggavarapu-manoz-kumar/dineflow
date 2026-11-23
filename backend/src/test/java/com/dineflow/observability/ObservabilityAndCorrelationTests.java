package com.dineflow.observability;

import com.dineflow.config.CorrelationIdFilter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class ObservabilityAndCorrelationTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private DineFlowMetricsService metricsService;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private CorrelationIdFilter correlationIdFilter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(correlationIdFilter)
                .apply(springSecurity())
                .build();
    }

    @Test
    void correlationIdPropagatesValidHeaderAndSanitizesLogForgingAttempts() throws Exception {
        // Valid header echoed back
        mockMvc.perform(get("/api/v1/health").header("X-Correlation-ID", "req-trace-2026-abc"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "req-trace-2026-abc"));

        // Malicious CRLF log-forging header must be rejected and replaced with clean UUID
        assertFalse(CorrelationIdFilter.isSafe("evil\r\nFAKE LOG LINE"));
        assertTrue(CorrelationIdFilter.isSafe("valid_corr-1234"));

        String returnedHeader = mockMvc.perform(get("/api/v1/health").header("X-Correlation-ID", "bad\nvalue"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader("X-Correlation-ID");
        assertNotNull(returnedHeader);
        assertFalse(returnedHeader.contains("bad"));
    }

    @Test
    void businessMetricsAndOperationalGaugesAreRegisteredWithoutHighCardinalityTags() {
        metricsService.recordPaymentInitiation("RAZORPAY", "ONLINE_PAYMENT", "initiated");
        metricsService.recordPaymentSettlement("ONLINE_PAYMENT", "webhook");
        metricsService.recordWebhook("RAZORPAY", "processed");
        metricsService.recordRefundExecution("ONLINE_GATEWAY", "succeeded");
        metricsService.recordOrderTransition("READY", "succeeded");
        metricsService.recordStorageOperation("S3_COMPATIBLE", "upload", "success");

        assertTrue(meterRegistry.counter("dineflow.payments.initiations",
                "provider", "razorpay", "method", "online_payment", "outcome", "initiated").count() >= 1.0);
        assertNotNull(meterRegistry.find("dineflow.outbox.backlog").tag("status", "pending").gauge());
        assertNotNull(meterRegistry.find("dineflow.reconciliation.pending_confirmation").gauge());
        assertNotNull(meterRegistry.find("dineflow.kitchen.active_queue").gauge());
    }

    @Test
    void actuatorHealthIsPublicWithoutLeakingSecretsAndPrometheusIsProtected() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("UP")))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("secret"))));

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }
}
