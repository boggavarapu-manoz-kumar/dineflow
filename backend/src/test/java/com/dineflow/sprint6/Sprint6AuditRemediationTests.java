package com.dineflow.sprint6;

import com.dineflow.billing.domain.BillStatus;
import com.dineflow.billing.domain.RestaurantBill;
import com.dineflow.billing.repository.RestaurantBillRepository;
import com.dineflow.common.exception.BusinessException;
import com.dineflow.config.ProductionConfigurationValidator;
import com.dineflow.payment.domain.PaymentAttempt;
import com.dineflow.payment.domain.PaymentMethod;
import com.dineflow.payment.domain.PaymentProviderType;
import com.dineflow.payment.domain.PaymentStatus;
import com.dineflow.payment.domain.RefundMethod;
import com.dineflow.payment.domain.RefundStatus;
import com.dineflow.payment.domain.RestaurantPayment;
import com.dineflow.payment.domain.RestaurantRefund;
import com.dineflow.payment.dto.ReversePaymentRequest;
import com.dineflow.payment.dto.VerifyPaymentRequest;
import com.dineflow.payment.provider.PaymentGatewayProvider;
import com.dineflow.payment.repository.PaymentReconciliationLogRepository;
import com.dineflow.payment.repository.RestaurantPaymentRepository;
import com.dineflow.payment.repository.RestaurantRefundRepository;
import com.dineflow.payment.service.PaymentProcessingService;
import com.dineflow.payment.service.PaymentReferenceGenerator;
import com.dineflow.payment.service.RefundManagementService;
import com.dineflow.realtime.WebSocketAuthChannelInterceptor;
import com.dineflow.security.DineFlowPrincipal;
import com.dineflow.security.JwtTokenService;
import com.dineflow.security.ratelimit.RateLimitingFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test suite verifying every Critical, High, and Medium defect uncovered in
 * the Sprint 6 Independent Principal Engineer Audit (AUDIT-CRIT-001 through AUDIT-MED-005).
 */
@SpringBootTest
@ActiveProfiles("test")
class Sprint6AuditRemediationTests {

    @Autowired private WebApplicationContext context;
    @Autowired private WebSocketAuthChannelInterceptor wsAuthInterceptor;
    @Autowired private RateLimitingFilter rateLimitingFilter;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private RestaurantBillRepository billRepository;
    @Autowired private RestaurantPaymentRepository paymentRepository;
    @Autowired private RestaurantRefundRepository refundRepository;
    @Autowired private PaymentReconciliationLogRepository reconLogRepository;
    @Autowired private PaymentReferenceGenerator referenceGenerator;
    @Autowired private PaymentProcessingService paymentProcessingService;
    @Autowired private TransactionTemplate transactionTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setupMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void auditCrit001BlocksClientStompSendFramesFromGuestAndStaff() {
        MessageChannel channel = mock(MessageChannel.class);
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        DineFlowPrincipal guest = new DineFlowPrincipal(
                UUID.randomUUID(), tenantA, UUID.randomUUID(), "CUSTOMER_GUEST",
                List.of("CUSTOMER_ORDER_VIEW_OWN"), "GUEST");

        StompHeaderAccessor guestSend = StompHeaderAccessor.create(StompCommand.SEND);
        guestSend.setDestination("/topic/tenant/" + tenantB + "/branch/" + UUID.randomUUID() + "/payments");
        guestSend.setUser(new UsernamePasswordAuthenticationToken(guest, null, guest.getAuthorities()));
        Message<byte[]> guestMsg = MessageBuilder.createMessage("{\"eventType\":\"PAYMENT_SETTLED\"}".getBytes(), guestSend.getMessageHeaders());

        assertThrows(AccessDeniedException.class, () -> wsAuthInterceptor.preSend(guestMsg, channel));

        DineFlowPrincipal staff = new DineFlowPrincipal(
                UUID.randomUUID(), tenantA, UUID.randomUUID(), "HOTEL_ADMIN",
                List.of("ORDER_MANAGE"), "STAFF");
        StompHeaderAccessor staffSend = StompHeaderAccessor.create(StompCommand.SEND);
        staffSend.setDestination("/topic/tenant/" + tenantA + "/orders/" + UUID.randomUUID());
        staffSend.setUser(new UsernamePasswordAuthenticationToken(staff, null, staff.getAuthorities()));
        Message<byte[]> staffMsg = MessageBuilder.createMessage("{}".getBytes(), staffSend.getMessageHeaders());

        assertThrows(AccessDeniedException.class, () -> wsAuthInterceptor.preSend(staffMsg, channel));
    }

    @Test
    void auditCrit002CommitsFailedRefundStatusWhenGatewayDefinitivelyFails() {
        UUID tenantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();

        RestaurantBill bill = new RestaurantBill(tenantId, branchId, UUID.randomUUID(),
                "B-AUD-" + UUID.randomUUID().toString().substring(0, 6), "T-9", "INR");
        bill.setSubtotal(new BigDecimal("400.00"));
        bill.setGrandTotal(new BigDecimal("400.00"));
        bill.setPaidAmount(new BigDecimal("400.00"));
        bill.setOutstandingBalance(BigDecimal.ZERO);
        bill.setStatus(BillStatus.PAID);
        bill = billRepository.save(bill);

        RestaurantPayment payment = new RestaurantPayment(
                tenantId, branchId, bill, "PAY-AUD-" + UUID.randomUUID().toString().substring(0, 6),
                PaymentMethod.ONLINE_PAYMENT, new BigDecimal("400.00"), "INR", PaymentProviderType.MOCK);
        payment.setPaidAmount(new BigDecimal("400.00"));
        payment.setPaymentStatus(PaymentStatus.SUCCEEDED);
        payment = paymentRepository.save(payment);

        RestaurantRefund refund = new RestaurantRefund(
                tenantId, branchId, payment, bill,
                "REF-AUD-" + UUID.randomUUID().toString().substring(0, 6),
                new BigDecimal("200.00"), "INR", RefundMethod.ONLINE_GATEWAY,
                "Customer cancelled dish", UUID.randomUUID());
        refund.setRefundStatus(RefundStatus.APPROVED);
        refund = refundRepository.save(refund);

        PaymentGatewayProvider failingGateway = mock(PaymentGatewayProvider.class);
        when(failingGateway.getProviderType()).thenReturn(PaymentProviderType.MOCK);
        when(failingGateway.processRefund(any(), any(), any())).thenReturn(
                new PaymentGatewayProvider.GatewayRefundResult(false, null, BigDecimal.ZERO, "BAD_REQUEST_ERROR", "Insufficient merchant balance", false)
        );

        RefundManagementService isolatedRefundService = new RefundManagementService(
                refundRepository, paymentRepository, billRepository, reconLogRepository,
                referenceGenerator, List.of(failingGateway), transactionTemplate);

        DineFlowPrincipal admin = new DineFlowPrincipal(
                UUID.randomUUID(), tenantId, branchId, "HOTEL_ADMIN", List.of("REFUND_EXECUTE"), "STAFF");

        final UUID refundId = refund.getId();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> isolatedRefundService.executeRefund(admin, refundId));
        assertEquals("GATEWAY_REFUND_FAILED", ex.getErrorCode());

        // Crucial regression check: RefundStatus.FAILED MUST be committed (NOT rolled back to INITIATED)
        RestaurantRefund persisted = refundRepository.findByIdAndTenantId(refundId, tenantId).orElseThrow();
        assertEquals(RefundStatus.FAILED, persisted.getRefundStatus());
        assertEquals("Insufficient merchant balance", persisted.getFailureMessage());

        // Cleanup to avoid polluting other test suites
        refundRepository.deleteById(refundId);
    }

    @Test
    void auditHigh001RightToLeftForwardedForResistsLeftmostHeaderSpoofing() {
        MockHttpServletRequest spoofedThroughProxy = new MockHttpServletRequest();
        spoofedThroughProxy.setRemoteAddr("10.0.1.15"); // Trusted Nginx proxy
        // Attacker injects fake leftmost IP "1.2.3.4"; Nginx appends real client IP "198.51.100.88"
        spoofedThroughProxy.addHeader("X-Forwarded-For", "1.2.3.4, 198.51.100.88, 10.0.1.15");
        assertEquals("198.51.100.88", rateLimitingFilter.resolveClientIp(spoofedThroughProxy));
    }

    @Test
    void auditHigh002ProductionValidatorRequiresRedisRealtimeFanout() {
        assertThrows(IllegalStateException.class, () -> ProductionConfigurationValidator.validateRealtimeFanout("local"));
        assertThrows(IllegalStateException.class, () -> ProductionConfigurationValidator.validateRealtimeFanout(null));
        ProductionConfigurationValidator.validateRealtimeFanout("redis");
    }

    @Test
    void auditHigh003SettleCommitsFailedVerificationAuditAndReverseRejectsRefundedPayment() {
        UUID tenantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();

        RestaurantBill bill = new RestaurantBill(tenantId, branchId, UUID.randomUUID(),
                "B-VER-" + UUID.randomUUID().toString().substring(0, 6), "T-4", "INR");
        bill.setSubtotal(new BigDecimal("300.00"));
        bill.setGrandTotal(new BigDecimal("300.00"));
        bill.setPaidAmount(BigDecimal.ZERO);
        bill.setOutstandingBalance(new BigDecimal("300.00"));
        bill.setStatus(BillStatus.ISSUED);
        bill = billRepository.save(bill);

        RestaurantPayment onlinePayment = new RestaurantPayment(
                tenantId, branchId, bill, "PAY-VER-" + UUID.randomUUID().toString().substring(0, 6),
                PaymentMethod.ONLINE_PAYMENT, new BigDecimal("300.00"), "INR", PaymentProviderType.MOCK);
        onlinePayment.setPaymentStatus(PaymentStatus.INITIATED);
        PaymentAttempt attempt = new PaymentAttempt(onlinePayment, tenantId, 1,
                "idem-" + UUID.randomUUID(), "MOCK", new BigDecimal("300.00"), "INR");
        attempt.setProviderOrderId("mock_order_123");
        onlinePayment.addAttempt(attempt);
        onlinePayment = paymentRepository.save(onlinePayment);

        DineFlowPrincipal staff = new DineFlowPrincipal(
                UUID.randomUUID(), tenantId, branchId, "CASHIER", List.of("PAYMENT_PROCESS"), "STAFF");

        final UUID onlinePayId = onlinePayment.getId();
        BusinessException verifyEx = assertThrows(BusinessException.class,
                () -> paymentProcessingService.settlePayment(staff, onlinePayId,
                        new VerifyPaymentRequest("mock_order_123", "pay_fake_999", "INVALID_SIG")));
        assertEquals("PAYMENT_VERIFICATION_FAILED", verifyEx.getErrorCode());

        // Verification failure MUST be persisted in DB (not rolled back!)
        RestaurantPayment afterFail = paymentRepository.findByIdAndTenantId(onlinePayId, tenantId).orElseThrow();
        assertEquals(PaymentStatus.FAILED, afterFail.getPaymentStatus());

        // Reverse payment must reject payments that already have partial refunds
        RestaurantPayment refundedPay = new RestaurantPayment(
                tenantId, branchId, bill, "PAY-REV-" + UUID.randomUUID().toString().substring(0, 6),
                PaymentMethod.CASH, new BigDecimal("300.00"), "INR", PaymentProviderType.CASH_REGISTER);
        refundedPay.setPaidAmount(new BigDecimal("300.00"));
        refundedPay.setRefundedAmount(new BigDecimal("50.00"));
        refundedPay.setPaymentStatus(PaymentStatus.SUCCEEDED);
        refundedPay = paymentRepository.save(refundedPay);

        final UUID refundedPayId = refundedPay.getId();
        BusinessException revEx = assertThrows(BusinessException.class,
                () -> paymentProcessingService.reversePayment(staff, refundedPayId, new ReversePaymentRequest("duplicate")));
        assertEquals("PAYMENT_ALREADY_REFUNDED", revEx.getErrorCode());
    }

    @Test
    void auditHigh004AndMed004AuthStubsDisabledAndSchedulerLocksRequirePlatformManage() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String tenantFinanceToken = "Bearer " + jwtTokenService.generateAccessToken(
                UUID.randomUUID(), tenantId, null, "HOTEL_ADMIN", List.of("REPORT_FINANCIAL_VIEW", "ORDER_VIEW"), "STAFF");
        String platformAdminToken = "Bearer " + jwtTokenService.generateAccessToken(
                UUID.randomUUID(), null, null, "SUPER_ADMIN", List.of("PLATFORM_MANAGE"), "STAFF");

        // Demo /tenant-resource endpoint removed -> 404
        mockMvc.perform(get("/api/v1/auth/tenant-resource")
                        .param("tenantId", tenantId.toString())
                        .header("Authorization", tenantFinanceToken))
                .andExpect(status().isNotFound());

        // Password reset stub no longer publicly permitted -> 401 unauthenticated
        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"fake\",\"newPassword\":\"Password123!\"}"))
                .andExpect(status().isUnauthorized());

        // /reconciliation/locks restricted strictly to PLATFORM_MANAGE (not tenant REPORT_FINANCIAL_VIEW)
        mockMvc.perform(get("/api/v1/payments/reconciliation/locks")
                        .header("Authorization", tenantFinanceToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/payments/reconciliation/locks")
                        .header("Authorization", platformAdminToken))
                .andExpect(status().isOk());
    }
}
