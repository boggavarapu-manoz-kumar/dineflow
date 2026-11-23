package com.dineflow.sprint6;

import com.dineflow.billing.domain.BillStatus;
import com.dineflow.billing.domain.RestaurantBill;
import com.dineflow.billing.repository.RestaurantBillRepository;
import com.dineflow.payment.domain.PaymentMethod;
import com.dineflow.payment.domain.PaymentProviderType;
import com.dineflow.payment.dto.InitiatePaymentRequest;
import com.dineflow.payment.dto.PaymentResponse;
import com.dineflow.payment.service.PaymentProcessingService;
import com.dineflow.realtime.WebSocketAuthChannelInterceptor;
import com.dineflow.security.DineFlowPrincipal;
import com.dineflow.security.ratelimit.InMemoryRateLimiterService;
import com.dineflow.security.ratelimit.RateLimiterService;
import com.dineflow.storage.BinaryObjectStorage;
import com.dineflow.tenant.domain.Tenant;
import com.dineflow.tenant.repository.TenantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class Sprint6ProductionReadinessTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private BinaryObjectStorage objectStorage;
    @Autowired private RateLimiterService rateLimiter;
    @Autowired private WebSocketAuthChannelInterceptor wsInterceptor;
    @Autowired private PaymentProcessingService paymentService;
    @Autowired private RestaurantBillRepository billRepository;
    @Autowired private TenantRepository tenantRepository;

    @Test
    @DisplayName("HTTP Layer: X-Correlation-ID, security headers, and 401 enforcement on protected financial routes")
    void httpSecurityHeadersAndCorrelationIdVerified() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));

        // Unauthenticated requests to financial endpoints must return 401
        mockMvc.perform(get("/api/v1/reports/financial"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/staff/bills"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/payments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Storage Abstraction: BinaryObjectStorage persists, retrieves, deletes, and blocks path traversal")
    void binaryObjectStorageRoundTripAndPathTraversalGuard() {
        String key = "documents/test/" + UUID.randomUUID() + "/sample.pdf";
        byte[] payload = "%PDF-1.4 sprint6".getBytes();

        objectStorage.putObject(key, payload, "application/pdf");
        assertThat(objectStorage.exists(key)).isTrue();
        assertThat(objectStorage.getObject(key)).contains(payload);

        objectStorage.deleteObject(key);
        assertThat(objectStorage.exists(key)).isFalse();

        assertThatThrownBy(() -> objectStorage.putObject("../escape.txt", payload, "text/plain"))
                .hasMessageContaining("Invalid storage object key");
    }

    @Test
    @DisplayName("Rate Limiter: Enforces per-window request cap and returns retryAfterSeconds")
    void rateLimiterEnforcesWindowCap() {
        String bucket = "test-bucket-" + UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            var d = rateLimiter.tryConsume(bucket, 3, Duration.ofMinutes(1));
            assertThat(d.allowed()).isTrue();
        }
        var blocked = rateLimiter.tryConsume(bucket, 3, Duration.ofMinutes(1));
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.remaining()).isZero();
        assertThat(blocked.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("WebSocket Auth: Blocks cross-tenant, cross-branch, and guest subscriptions to staff topics")
    void webSocketSubscriptionAuthorizationEnforced() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID branch1 = UUID.randomUUID();
        UUID branch2 = UUID.randomUUID();

        DineFlowPrincipal staffA1 = new DineFlowPrincipal(UUID.randomUUID(), tenantA, branch1, "WAITER", List.of("ORDER_VIEW"), "STAFF");
        DineFlowPrincipal guestA1 = new DineFlowPrincipal(UUID.randomUUID(), tenantA, branch1, "GUEST", List.of("CUSTOMER_ORDER_VIEW_OWN"), "GUEST");

        // 1. Authorized staff subscribing to own tenant/branch kitchen topic -> ALLOWED
        StompHeaderAccessor okAcc = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        okAcc.setUser(new UsernamePasswordAuthenticationToken(staffA1, null, staffA1.getAuthorities()));
        okAcc.setDestination("/topic/tenant/" + tenantA + "/branch/" + branch1 + "/kitchen");
        wsInterceptor.authorizeSubscription(okAcc);

        // 2. Cross-tenant subscription -> REJECTED
        StompHeaderAccessor crossTenant = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        crossTenant.setUser(new UsernamePasswordAuthenticationToken(staffA1, null, staffA1.getAuthorities()));
        crossTenant.setDestination("/topic/tenant/" + tenantB + "/branch/" + branch1 + "/kitchen");
        assertThatThrownBy(() -> wsInterceptor.authorizeSubscription(crossTenant))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Cross-tenant");

        // 3. Cross-branch subscription -> REJECTED
        StompHeaderAccessor crossBranch = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        crossBranch.setUser(new UsernamePasswordAuthenticationToken(staffA1, null, staffA1.getAuthorities()));
        crossBranch.setDestination("/topic/tenant/" + tenantA + "/branch/" + branch2 + "/kitchen");
        assertThatThrownBy(() -> wsInterceptor.authorizeSubscription(crossBranch))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Cross-branch");

        // 4. Guest attempting to subscribe to staff kitchen topic -> REJECTED
        StompHeaderAccessor guestOnStaff = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        guestOnStaff.setUser(new UsernamePasswordAuthenticationToken(guestA1, null, guestA1.getAuthorities()));
        guestOnStaff.setDestination("/topic/tenant/" + tenantA + "/branch/" + branch1 + "/kitchen");
        assertThatThrownBy(() -> wsInterceptor.authorizeSubscription(guestOnStaff))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Guest sessions cannot subscribe");
    }

    @Test
    @DisplayName("Payment Initiation TX Boundary: 3-phase TransactionTemplate completes without active outer transaction")
    void paymentInitiationRunsGatewayOutsideOuterTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();

        Tenant t = new Tenant();
        t.setBusinessName("TX Boundary Hotel");
        t.setSlug("tx-boundary-" + UUID.randomUUID());
        t.setStatus("ACTIVE");
        t.setSubscriptionPlan("PRO");
        t = tenantRepository.save(t);

        RestaurantBill bill = new RestaurantBill();
        bill.setTenantId(t.getId());
        bill.setBillNumber("BILL-TX-" + UUID.randomUUID().toString().substring(0, 6));
        bill.setTableNumber("T-1");
        bill.setRestaurantNameSnapshot(t.getBusinessName());
        bill.setCurrencyCode("INR");
        bill.setSubtotal(new BigDecimal("250.00"));
        bill.setGrandTotal(new BigDecimal("250.00"));
        bill.setOutstandingBalance(new BigDecimal("250.00"));
        bill.setPaidAmount(BigDecimal.ZERO);
        bill.setStatus(BillStatus.ISSUED);
        bill = billRepository.save(bill);

        DineFlowPrincipal staff = new DineFlowPrincipal(UUID.randomUUID(), t.getId(), null, "HOTEL_ADMIN", List.of("PAYMENT_PROCESS"), "STAFF");
        PaymentResponse res = paymentService.initiatePayment(staff,
                new InitiatePaymentRequest(bill.getId(), PaymentMethod.ONLINE_PAYMENT, PaymentProviderType.MOCK, null, "Non-TX I/O test", null));

        assertThat(res.paymentStatus()).isEqualTo("INITIATED");
        assertThat(res.providerOrderId()).startsWith("mock_ord_");
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }
}
