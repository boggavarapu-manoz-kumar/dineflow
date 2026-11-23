package com.dineflow.payment;

import com.dineflow.billing.domain.BillStatus;
import com.dineflow.billing.domain.RestaurantBill;
import com.dineflow.billing.repository.RestaurantBillRepository;
import com.dineflow.common.exception.BusinessException;
import com.dineflow.payment.config.RazorpayProperties;
import com.dineflow.payment.domain.PaymentMethod;
import com.dineflow.payment.domain.PaymentProviderType;
import com.dineflow.payment.domain.PaymentStatus;
import com.dineflow.payment.dto.InitiatePaymentRequest;
import com.dineflow.payment.dto.PaymentResponse;
import com.dineflow.payment.dto.ReconciliationReport;
import com.dineflow.payment.dto.VerifyPaymentRequest;
import com.dineflow.payment.dto.WebhookProcessingMetrics;
import com.dineflow.payment.provider.RazorpayPaymentGatewayProvider;
import com.dineflow.payment.repository.PaymentAttemptRepository;
import com.dineflow.payment.repository.PaymentReconciliationLogRepository;
import com.dineflow.payment.repository.PaymentWebhookEventRepository;
import com.dineflow.payment.repository.RestaurantPaymentRepository;
import com.dineflow.payment.service.PaymentProcessingService;
import com.dineflow.security.DineFlowPrincipal;
import com.dineflow.tenant.domain.Tenant;
import com.dineflow.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
public class PaymentProcessingServiceTests {

    @Autowired
    private PaymentProcessingService paymentService;

    @Autowired
    private RestaurantPaymentRepository paymentRepository;

    @Autowired
    private PaymentAttemptRepository attemptRepository;

    @Autowired
    private PaymentWebhookEventRepository webhookEventRepository;

    @Autowired
    private PaymentReconciliationLogRepository reconciliationLogRepository;

    @Autowired
    private RestaurantBillRepository billRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private RazorpayProperties razorpayProperties;

    private Tenant testTenant;
    private RestaurantBill testBill;
    private DineFlowPrincipal staffPrincipal;

    @BeforeEach
    void setUp() {
        reconciliationLogRepository.deleteAll();
        webhookEventRepository.deleteAll();
        attemptRepository.deleteAll();
        paymentRepository.deleteAll();
        billRepository.deleteAll();

        testTenant = tenantRepository.findAll().stream().findFirst().orElseGet(() -> {
            Tenant t = new Tenant();
            t.setBusinessName("Payment Test Hotel");
            t.setSlug("payment-test-" + UUID.randomUUID());
            t.setStatus("ACTIVE");
            t.setSubscriptionPlan("PRO");
            return tenantRepository.save(t);
        });

        testBill = new RestaurantBill();
        testBill.setTenantId(testTenant.getId());
        testBill.setBillNumber("BILL-20261122-PAYTEST");
        testBill.setTableNumber("T-01");
        testBill.setRestaurantNameSnapshot("Payment Test Hotel");
        testBill.setCurrencyCode("INR");
        testBill.setSubtotal(BigDecimal.valueOf(450.00));
        testBill.setTaxAmount(BigDecimal.valueOf(50.00));
        testBill.setGrandTotal(BigDecimal.valueOf(500.00));
        testBill.setOutstandingBalance(BigDecimal.valueOf(500.00));
        testBill.setPaidAmount(BigDecimal.ZERO);
        testBill.setStatus(BillStatus.ISSUED);
        testBill = billRepository.save(testBill);

        staffPrincipal = new DineFlowPrincipal(
                UUID.randomUUID(),
                testTenant.getId(),
                null,
                "HOTEL_ADMIN",
                List.of("PAYMENT_PROCESS", "PAYMENT_VIEW", "BILL_COLLECT_PAYMENT"),
                "STAFF"
        );
    }

    @Test
    @DisplayName("Online payment initiation with Razorpay/Mock generates correct order parameters")
    void testRazorpayPaymentInitiation() {
        InitiatePaymentRequest request = new InitiatePaymentRequest(
                testBill.getId(),
                PaymentMethod.ONLINE_PAYMENT,
                PaymentProviderType.MOCK,
                BigDecimal.valueOf(500.00),
                "Customer table payment",
                "http://localhost:5173/payment/callback"
        );

        PaymentResponse response = paymentService.initiatePayment(staffPrincipal, request);

        assertThat(response).isNotNull();
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.INITIATED.name());
        assertThat(response.payableAmount()).isEqualByComparingTo(BigDecimal.valueOf(500.00));
        assertThat(response.providerOrderId()).isNotNull();
        assertThat(response.clientRedirectUrl()).contains("mock_ord_");
    }

    @Test
    @DisplayName("Razorpay signature verification succeeds when HMAC-SHA256 matches")
    void testRazorpaySignatureVerification() throws Exception {
        String orderId = "order_rzp_12345";
        String paymentId = "pay_rzp_98765";
        String secret = razorpayProperties.getKeySecret();
        String validSignature = RazorpayPaymentGatewayProvider.calculateHmacSha256(orderId + "|" + paymentId, secret);

        RazorpayPaymentGatewayProvider provider = new RazorpayPaymentGatewayProvider(razorpayProperties);
        var verificationResult = provider.verifyPayment(orderId, paymentId, validSignature);

        assertThat(verificationResult.isVerified()).isTrue();
        assertThat(verificationResult.providerTransactionId()).isEqualTo(paymentId);

        var badResult = provider.verifyPayment(orderId, paymentId, "invalid_tampered_signature");
        assertThat(badResult.isVerified()).isFalse();
        assertThat(badResult.errorCode()).isEqualTo("SIGNATURE_MISMATCH");
    }

    @Test
    @DisplayName("Cash payment immediately settles and updates bill balances")
    void testCashPaymentImmediateSettlement() {
        InitiatePaymentRequest initReq = new InitiatePaymentRequest(
                testBill.getId(),
                PaymentMethod.CASH,
                PaymentProviderType.CASH_REGISTER,
                BigDecimal.valueOf(500.00),
                "Cash at billing counter",
                null
        );
        PaymentResponse initRes = paymentService.initiatePayment(staffPrincipal, initReq);

        VerifyPaymentRequest settleReq = new VerifyPaymentRequest("CASH-TXN-001", null, "MANUAL_VERIFIED");
        PaymentResponse settledRes = paymentService.settlePayment(staffPrincipal, initRes.id(), settleReq);

        assertThat(settledRes.paymentStatus()).isEqualTo(PaymentStatus.SUCCEEDED.name());
        assertThat(settledRes.paidAmount()).isEqualByComparingTo(BigDecimal.valueOf(500.00));

        RestaurantBill updatedBill = billRepository.findById(testBill.getId()).orElseThrow();
        assertThat(updatedBill.getStatus()).isEqualTo(BillStatus.PAID);
        assertThat(updatedBill.getPaidAmount()).isEqualByComparingTo(BigDecimal.valueOf(500.00));
        assertThat(updatedBill.getOutstandingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Webhook idempotency: Duplicate webhook events are ignored safely")
    void testDuplicateWebhookIgnored() {
        String eventPayload = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_12345\",\"amount\":50000,\"currency\":\"INR\"}}}}";
        Map<String, String> headers = Map.of(
                "x-razorpay-signature", "mock_signature_test",
                "x-razorpay-event-id", "evt_123456"
        );

        assertThat(webhookEventRepository.existsByProviderNameAndProviderEventId("MOCK", "evt_123456")).isFalse();
        paymentService.processProviderWebhook("MOCK", eventPayload, headers);

        assertThat(webhookEventRepository.existsByProviderNameAndProviderEventId("MOCK", "evt_123456")).isTrue();

        paymentService.processProviderWebhook("MOCK", eventPayload, headers);
        assertThat(webhookEventRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Out-of-order webhook events do not downgrade an already settled payment")
    void testOutOfOrderWebhookDoesNotDowngradeSettledPayment() {
        // 1. Settle payment first
        InitiatePaymentRequest initReq = new InitiatePaymentRequest(
                testBill.getId(),
                PaymentMethod.ONLINE_PAYMENT,
                PaymentProviderType.MOCK,
                BigDecimal.valueOf(500.00),
                "Online payment",
                null
        );
        PaymentResponse initRes = paymentService.initiatePayment(staffPrincipal, initReq);
        paymentService.settlePayment(staffPrincipal, initRes.id(), new VerifyPaymentRequest("txn_mock_settled", initRes.providerOrderId(), "mock_sig"));

        // 2. Incoming late webhook for the same order
        String latePayload = "{\"event\":\"payment.pending\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"txn_mock_settled\",\"amount\":50000,\"currency\":\"INR\"}}}}";
        Map<String, String> headers = Map.of(
                "x-razorpay-signature", "mock_sig",
                "x-razorpay-event-id", "evt_late_001"
        );
        paymentService.processProviderWebhook("MOCK", latePayload, headers);

        // Payment status must remain SUCCEEDED
        PaymentResponse paymentAfter = paymentService.getPayment(staffPrincipal, initRes.id());
        assertThat(paymentAfter.paymentStatus()).isEqualTo(PaymentStatus.SUCCEEDED.name());
    }

    @Test
    @DisplayName("Staff cash payment collection supports partial payment and balance updates")
    void testStaffCashPartialPayment() {
        // Staff collects partial cash (300.00 out of 500.00)
        com.dineflow.payment.dto.RecordCashPaymentRequest cashReq = new com.dineflow.payment.dto.RecordCashPaymentRequest(
                testBill.getId(),
                BigDecimal.valueOf(300.00),
                "Customer paid partial cash"
        );

        PaymentResponse response = paymentService.recordStaffCashPayment(staffPrincipal, cashReq);

        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.SUCCEEDED.name());
        assertThat(response.paidAmount()).isEqualByComparingTo(BigDecimal.valueOf(300.00));

        // Bill state verification: PARTIALLY_PAID, 200.00 remaining
        RestaurantBill updatedBill = billRepository.findById(testBill.getId()).orElseThrow();
        assertThat(updatedBill.getStatus()).isEqualTo(BillStatus.PARTIALLY_PAID);
        assertThat(updatedBill.getPaidAmount()).isEqualByComparingTo(BigDecimal.valueOf(300.00));
        assertThat(updatedBill.getOutstandingBalance()).isEqualByComparingTo(BigDecimal.valueOf(200.00));
    }

    @Test
    @DisplayName("Payment reversal restores bill outstanding balance and marks payment REFUNDED")
    void testPaymentReversalRestoresBillBalance() {
        // 1. Settle cash payment
        com.dineflow.payment.dto.RecordCashPaymentRequest cashReq = new com.dineflow.payment.dto.RecordCashPaymentRequest(
                testBill.getId(),
                BigDecimal.valueOf(500.00),
                "Settled full bill"
        );
        PaymentResponse response = paymentService.recordStaffCashPayment(staffPrincipal, cashReq);

        // 2. Perform reversal
        com.dineflow.payment.dto.ReversePaymentRequest revReq = new com.dineflow.payment.dto.ReversePaymentRequest("Cashier input error correction");
        PaymentResponse reversed = paymentService.reversePayment(staffPrincipal, response.id(), revReq);

        assertThat(reversed.paymentStatus()).isEqualTo(PaymentStatus.REFUNDED.name());
        assertThat(reversed.refundedAmount()).isEqualByComparingTo(BigDecimal.valueOf(500.00));

        // Bill is restored to ISSUED with 500.00 balance
        RestaurantBill updatedBill = billRepository.findById(testBill.getId()).orElseThrow();
        assertThat(updatedBill.getStatus()).isEqualTo(BillStatus.ISSUED);
        assertThat(updatedBill.getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(updatedBill.getOutstandingBalance()).isEqualByComparingTo(BigDecimal.valueOf(500.00));
    }

    @Test
    @DisplayName("Cash collection shift summary computes received, reversed and net amounts correctly")
    void testCashCollectionShiftSummary() {
        // Collect 500 cash
        com.dineflow.payment.dto.RecordCashPaymentRequest cashReq = new com.dineflow.payment.dto.RecordCashPaymentRequest(
                testBill.getId(),
                BigDecimal.valueOf(500.00),
                "Counter payment"
        );
        PaymentResponse payment = paymentService.recordStaffCashPayment(staffPrincipal, cashReq);

        // Query summary
        com.dineflow.payment.dto.CashCollectionSummary summary = paymentService.getCashCollectionSummary(staffPrincipal, null, null);
        assertThat(summary.totalTransactions()).isEqualTo(1);
        assertThat(summary.totalCashReceived()).isEqualByComparingTo(BigDecimal.valueOf(500.00));
        assertThat(summary.netCashCollected()).isEqualByComparingTo(BigDecimal.valueOf(500.00));
    }

    @Test
    @DisplayName("Reconciliation report runs and surfaces operational metrics")
    void testReconciliationAndOperationalMetrics() {
        // Run reconciliation
        ReconciliationReport report = paymentService.reconcilePendingPayments(testTenant.getId());
        assertThat(report).isNotNull();
        assertThat(report.scannedCount()).isGreaterThanOrEqualTo(0);

        // Check Webhook metrics
        WebhookProcessingMetrics metrics = paymentService.getWebhookMetrics();
        assertThat(metrics).isNotNull();
        assertThat(metrics.totalReceived()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Payment cannot exceed outstanding balance")
    void testPaymentExceedingBalanceRejected() {
        InitiatePaymentRequest request = new InitiatePaymentRequest(
                testBill.getId(),
                PaymentMethod.CASH,
                PaymentProviderType.CASH_REGISTER,
                BigDecimal.valueOf(600.00),
                "Excess payment",
                null
        );

        assertThatThrownBy(() -> paymentService.initiatePayment(staffPrincipal, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Payment amount cannot exceed outstanding bill balance");
    }
}
