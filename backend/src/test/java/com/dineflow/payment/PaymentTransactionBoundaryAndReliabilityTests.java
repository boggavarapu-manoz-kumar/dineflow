package com.dineflow.payment;

import com.dineflow.billing.domain.BillStatus;
import com.dineflow.billing.domain.RestaurantBill;
import com.dineflow.billing.repository.RestaurantBillRepository;
import com.dineflow.common.exception.BusinessException;
import com.dineflow.payment.config.RazorpayProperties;
import com.dineflow.payment.domain.PaymentAttempt;
import com.dineflow.payment.domain.PaymentMethod;
import com.dineflow.payment.domain.PaymentProviderType;
import com.dineflow.payment.domain.PaymentStateMachine;
import com.dineflow.payment.domain.PaymentStatus;
import com.dineflow.payment.domain.RestaurantPayment;
import com.dineflow.payment.dto.InitiatePaymentRequest;
import com.dineflow.payment.dto.PaymentResponse;
import com.dineflow.payment.dto.ReconciliationReport;
import com.dineflow.payment.dto.VerifyPaymentRequest;
import com.dineflow.payment.provider.PaymentGatewayProvider;
import com.dineflow.payment.provider.RazorpayPaymentGatewayProvider;
import com.dineflow.payment.repository.PaymentAttemptRepository;
import com.dineflow.payment.repository.PaymentReconciliationLogRepository;
import com.dineflow.payment.repository.PaymentWebhookEventRepository;
import com.dineflow.payment.repository.RestaurantPaymentRepository;
import com.dineflow.payment.service.PaymentProcessingService;
import com.dineflow.payment.service.PaymentReferenceGenerator;
import com.dineflow.security.DineFlowPrincipal;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class PaymentTransactionBoundaryAndReliabilityTests {

    @Autowired
    private PaymentProcessingService paymentService;

    @Autowired
    private RestaurantBillRepository billRepository;

    @Autowired
    private RestaurantPaymentRepository paymentRepository;

    @Autowired
    private PaymentAttemptRepository attemptRepository;

    @Autowired
    private PaymentWebhookEventRepository webhookEventRepository;

    @Autowired
    private PaymentReconciliationLogRepository reconciliationLogRepository;

    @Autowired
    private PaymentReferenceGenerator referenceGenerator;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private HttpServer mockRazorpayServer;
    private UUID tenantId;
    private UUID branchId;
    private DineFlowPrincipal staffPrincipal;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        branchId = UUID.randomUUID();
        staffPrincipal = new DineFlowPrincipal(
                UUID.randomUUID(), tenantId, branchId, "CASHIER",
                List.of("PAYMENT_PROCESS", "PAYMENT_VIEW"), "STAFF"
        );
    }

    @AfterEach
    void tearDown() {
        if (mockRazorpayServer != null) {
            mockRazorpayServer.stop(0);
            mockRazorpayServer = null;
        }
    }

    private RestaurantBill createBill(BigDecimal grandTotal) {
        RestaurantBill bill = new RestaurantBill(
                tenantId, branchId, null, "B-" + UUID.randomUUID().toString().substring(0, 8),
                "T-10", "INR"
        );
        bill.setBranchNameSnapshot("Main Branch");
        bill.setSubtotal(grandTotal);
        bill.setGrandTotal(grandTotal);
        bill.setOutstandingBalance(grandTotal);
        bill.setStatus(BillStatus.ISSUED);
        return billRepository.save(bill);
    }

    private PaymentProcessingService buildServiceWithCustomProvider(PaymentGatewayProvider provider) {
        return new PaymentProcessingService(
                paymentRepository,
                attemptRepository,
                billRepository,
                webhookEventRepository,
                reconciliationLogRepository,
                referenceGenerator,
                List.of(provider),
                transactionTemplate,
                eventPublisher
        );
    }

    @Test
    void phaseBExternalProviderCallRunsOutsideDatabaseTransactionAndSendsIdempotencyHeader() throws Exception {
        AtomicInteger orderCalls = new AtomicInteger(0);
        AtomicReference<String> observedIdempotencyHeader = new AtomicReference<>();

        mockRazorpayServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mockRazorpayServer.createContext("/v1/orders", exchange -> {
            orderCalls.incrementAndGet();
            observedIdempotencyHeader.set(exchange.getRequestHeaders().getFirst("X-Idempotency-Key"));
            String body = "{\"id\":\"order_rzp_tx_boundary_1\",\"status\":\"created\"}";
            exchange.sendResponseHeaders(200, body.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        });
        mockRazorpayServer.start();

        RazorpayProperties props = new RazorpayProperties();
        props.setEnabled(true);
        props.setKeyId("rzp_test_key");
        props.setKeySecret("rzp_test_secret");
        props.setWebhookSecret("rzp_wh_secret");
        props.setBaseUrl("http://127.0.0.1:" + mockRazorpayServer.getAddress().getPort() + "/v1");

        RazorpayPaymentGatewayProvider realProvider = new RazorpayPaymentGatewayProvider(props);
        AtomicBoolean txActiveDuringHttpCall = new AtomicBoolean(true);

        PaymentGatewayProvider txObservingProvider = new PaymentGatewayProvider() {
            @Override
            public PaymentProviderType getProviderType() {
                return PaymentProviderType.RAZORPAY;
            }

            @Override
            public GatewayOrderResult createPaymentOrder(RestaurantBill bill, RestaurantPayment payment, PaymentAttempt attempt, String callbackUrl) {
                txActiveDuringHttpCall.set(TransactionSynchronizationManager.isActualTransactionActive());
                return realProvider.createPaymentOrder(bill, payment, attempt, callbackUrl);
            }

            @Override
            public GatewayVerificationResult verifyPayment(String providerOrderId, String providerTransactionId, String signaturePayload) {
                return realProvider.verifyPayment(providerOrderId, providerTransactionId, signaturePayload);
            }

            @Override
            public GatewayVerificationResult verifyWebhook(String rawPayload, Map<String, String> headers) {
                return realProvider.verifyWebhook(rawPayload, headers);
            }

            @Override
            public GatewayRefundResult processRefund(RestaurantPayment payment, BigDecimal amount, String reason) {
                return realProvider.processRefund(payment, amount, reason);
            }
        };

        PaymentProcessingService isolatedService = buildServiceWithCustomProvider(txObservingProvider);
        RestaurantBill bill = createBill(new BigDecimal("450.00"));
        String idemKey = "idem-tx-proof-" + UUID.randomUUID();

        PaymentResponse response = isolatedService.initiatePayment(
                staffPrincipal,
                new InitiatePaymentRequest(bill.getId(), PaymentMethod.ONLINE_PAYMENT, PaymentProviderType.RAZORPAY,
                        new BigDecimal("450.00"), "Tx proof", null),
                idemKey
        );

        assertFalse(txActiveDuringHttpCall.get(), "External HTTP call MUST NOT execute inside an active DB transaction");
        assertEquals("INITIATED", response.paymentStatus());
        assertEquals("order_rzp_tx_boundary_1", response.providerOrderId());
        assertEquals(idemKey, observedIdempotencyHeader.get());
        assertEquals(1, orderCalls.get());
    }

    @Test
    void providerReadTimeoutMarksPendingConfirmationNotFailedAndReconciliationRecovers() throws Exception {
        AtomicInteger pollMode = new AtomicInteger(0); // 0 -> 503 transient, 1 -> 200 captured

        mockRazorpayServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mockRazorpayServer.createContext("/v1/orders", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod()) && path.equals("/v1/orders")) {
                try {
                    Thread.sleep(900); // Exceeds 500ms read timeout
                } catch (InterruptedException ignored) {
                }
                String body = "{\"id\":\"order_late_timeout\"}";
                exchange.sendResponseHeaders(200, body.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
            } else if ("GET".equalsIgnoreCase(exchange.getRequestMethod()) && path.endsWith("/payments")) {
                if (pollMode.get() == 0) {
                    String err = "{\"error\":\"temporarily unavailable\"}";
                    exchange.sendResponseHeaders(503, err.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(err.getBytes(StandardCharsets.UTF_8));
                    }
                } else {
                    String captured = "{\"items\":[{\"id\":\"pay_recovered_999\",\"status\":\"captured\",\"amount\":30000,\"currency\":\"INR\"}]}";
                    exchange.sendResponseHeaders(200, captured.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(captured.getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
        });
        mockRazorpayServer.start();

        RazorpayProperties props = new RazorpayProperties();
        props.setEnabled(true);
        props.setKeyId("rzp_test_key");
        props.setKeySecret("rzp_test_secret");
        props.setWebhookSecret("rzp_wh_secret");
        props.setReadTimeoutMs(500);
        props.setConnectTimeoutMs(500);
        props.setBaseUrl("http://127.0.0.1:" + mockRazorpayServer.getAddress().getPort() + "/v1");

        RazorpayPaymentGatewayProvider realProvider = new RazorpayPaymentGatewayProvider(props);
        AtomicBoolean txActiveDuringPoll = new AtomicBoolean(true);

        PaymentGatewayProvider pollingSpy = new PaymentGatewayProvider() {
            @Override
            public PaymentProviderType getProviderType() { return PaymentProviderType.RAZORPAY; }

            @Override
            public GatewayOrderResult createPaymentOrder(RestaurantBill bill, RestaurantPayment payment, PaymentAttempt attempt, String callbackUrl) {
                return realProvider.createPaymentOrder(bill, payment, attempt, callbackUrl);
            }

            @Override
            public GatewayVerificationResult verifyPayment(String providerOrderId, String providerTransactionId, String signaturePayload) {
                return realProvider.verifyPayment(providerOrderId, providerTransactionId, signaturePayload);
            }

            @Override
            public GatewayVerificationResult verifyWebhook(String rawPayload, Map<String, String> headers) {
                return realProvider.verifyWebhook(rawPayload, headers);
            }

            @Override
            public GatewayVerificationResult fetchOrderStatus(String providerOrderId) {
                txActiveDuringPoll.set(TransactionSynchronizationManager.isActualTransactionActive());
                return realProvider.fetchOrderStatus(providerOrderId);
            }

            @Override
            public GatewayRefundResult processRefund(RestaurantPayment payment, BigDecimal amount, String reason) {
                return realProvider.processRefund(payment, amount, reason);
            }
        };

        PaymentProcessingService isolatedService = buildServiceWithCustomProvider(pollingSpy);
        RestaurantBill bill = createBill(new BigDecimal("300.00"));

        PaymentResponse timedOutResp = isolatedService.initiatePayment(
                staffPrincipal,
                new InitiatePaymentRequest(bill.getId(), PaymentMethod.ONLINE_PAYMENT, PaymentProviderType.RAZORPAY,
                        new BigDecimal("300.00"), "Timeout test", null)
        );

        // Must be PENDING_CONFIRMATION, NEVER FAILED!
        assertEquals("PENDING_CONFIRMATION", timedOutResp.paymentStatus());

        // Attach providerOrderId as if recovered from provider lookup/webhook metadata, then test reconciliation
        transactionTemplate.executeWithoutResult(status -> {
            RestaurantPayment p = paymentRepository.findByIdAndTenantId(timedOutResp.id(), tenantId).orElseThrow();
            PaymentAttempt a = p.getAttempts().get(0);
            assertTrue(a.isAmbiguousOutcome());
            a.setProviderOrderId("order_late_timeout");
            paymentRepository.save(p);
        });

        // 1st reconciliation: provider returns 503 (temporarily unavailable) -> MUST remain PENDING_CONFIRMATION
        ReconciliationReport unavailableReport = isolatedService.reconcilePendingPayments(tenantId);
        assertFalse(txActiveDuringPoll.get(), "Reconciliation provider status poll MUST NOT hold a DB transaction");
        assertEquals(1, unavailableReport.discrepancyCount());
        assertEquals(0, unavailableReport.timedOutCount());
        RestaurantPayment stillPending = paymentRepository.findByIdAndTenantId(timedOutResp.id(), tenantId).orElseThrow();
        assertEquals(PaymentStatus.PENDING_CONFIRMATION, stillPending.getPaymentStatus());

        // 2nd reconciliation (simulate exponential backoff cooldown elapsed): provider returns captured -> settles bill
        transactionTemplate.executeWithoutResult(status -> {
            RestaurantPayment p = paymentRepository.findByIdAndTenantId(timedOutResp.id(), tenantId).orElseThrow();
            p.getAttempts().get(0).setNextReconciliationAt(java.time.Instant.now().minusSeconds(5));
            paymentRepository.save(p);
        });
        pollMode.set(1);
        ReconciliationReport recoveredReport = isolatedService.reconcilePendingPayments(tenantId);
        assertEquals(1, recoveredReport.resolvedCount());

        RestaurantPayment recovered = paymentRepository.findByIdAndTenantId(timedOutResp.id(), tenantId).orElseThrow();
        assertEquals(PaymentStatus.SUCCEEDED, recovered.getPaymentStatus());
        assertEquals("pay_recovered_999", recovered.getProviderPaymentId());

        RestaurantBill updatedBill = billRepository.findByIdAndTenantId(bill.getId(), tenantId).orElseThrow();
        assertEquals(BillStatus.PAID, updatedBill.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(updatedBill.getOutstandingBalance()));
    }

    @Test
    void idempotencyKeyReplaysSamePayloadAndRejectsDifferentPayload() {
        RestaurantBill bill = createBill(new BigDecimal("600.00"));
        String idemKey = "checkout-idem-" + UUID.randomUUID();

        InitiatePaymentRequest req1 = new InitiatePaymentRequest(
                bill.getId(), PaymentMethod.ONLINE_PAYMENT, PaymentProviderType.MOCK,
                new BigDecimal("600.00"), "First click", null
        );
        PaymentResponse first = paymentService.initiatePayment(staffPrincipal, req1, idemKey);
        PaymentResponse replay = paymentService.initiatePayment(staffPrincipal, req1, idemKey);

        assertEquals(first.id(), replay.id());
        assertEquals(first.paymentReference(), replay.paymentReference());
        assertEquals(first.providerOrderId(), replay.providerOrderId());

        // Reuse same idempotency key with a different amount -> 409 Conflict
        InitiatePaymentRequest mutatedReq = new InitiatePaymentRequest(
                bill.getId(), PaymentMethod.ONLINE_PAYMENT, PaymentProviderType.MOCK,
                new BigDecimal("250.00"), "Mutated payload", null
        );
        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentService.initiatePayment(staffPrincipal, mutatedReq, idemKey));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("IDEMPOTENCY_KEY_PAYLOAD_MISMATCH", ex.getErrorCode());
    }

    @Test
    void webhookArrivingBeforePhaseCPersistenceSettlesPaymentAndPhaseCNeverDowngradesSucceeded() {
        RestaurantBill bill = createBill(new BigDecimal("400.00"));
        AtomicReference<String> capturedPaymentRef = new AtomicReference<>();

        // Custom provider that fires a verified webhook DURING Phase B (before Phase C runs!),
        // and then returns a simulated timeout to Phase C.
        PaymentGatewayProvider raceSimulatingProvider = new PaymentGatewayProvider() {
            @Override
            public PaymentProviderType getProviderType() { return PaymentProviderType.MOCK; }

            @Override
            public GatewayOrderResult createPaymentOrder(RestaurantBill b, RestaurantPayment p, PaymentAttempt a, String cb) {
                capturedPaymentRef.set(p.getPaymentReference());
                // Webhook arrives BEFORE Phase C persists providerOrderId!
                String earlyWebhookJson = String.format(
                        "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_early_wh_1\",\"order_id\":\"ord_early_wh_1\",\"amount\":40000,\"currency\":\"INR\",\"notes\":{\"paymentReference\":\"%s\"}}}}}",
                        p.getPaymentReference()
                );
                paymentService.processProviderWebhook("MOCK", earlyWebhookJson, Map.of("x-razorpay-event-id", "evt_early_" + UUID.randomUUID()));

                // Now Phase B returns an ambiguous timeout — Phase C MUST NOT downgrade SUCCEEDED!
                return new GatewayOrderResult(false, null, null, null, "GATEWAY_READ_TIMEOUT", "Simulated late timeout", null, true);
            }

            @Override
            public GatewayVerificationResult verifyPayment(String o, String t, String s) {
                return new GatewayVerificationResult(true, t, null, "INR", null, null);
            }

            @Override
            public GatewayVerificationResult verifyWebhook(String r, Map<String, String> h) {
                return new GatewayVerificationResult(true, "pay_early_wh_1", new BigDecimal("400.00"), "INR", null, "payment.captured");
            }

            @Override
            public GatewayRefundResult processRefund(RestaurantPayment p, BigDecimal a, String r) {
                return new GatewayRefundResult(true, "ref_1", a, null, null);
            }
        };

        PaymentProcessingService raceService = buildServiceWithCustomProvider(raceSimulatingProvider);
        PaymentResponse result = raceService.initiatePayment(
                staffPrincipal,
                new InitiatePaymentRequest(bill.getId(), PaymentMethod.ONLINE_PAYMENT, PaymentProviderType.MOCK,
                        new BigDecimal("400.00"), "Webhook race", null)
        );

        // Payment must stay SUCCEEDED even though Phase B returned timeout!
        assertEquals("SUCCEEDED", result.paymentStatus());

        // Delayed payment.failed webhook must also be ignored and NEVER downgrade SUCCEEDED
        String delayedFailedWebhook = String.format(
                "{\"event\":\"payment.failed\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_early_wh_1\",\"order_id\":\"ord_early_wh_1\",\"amount\":40000,\"currency\":\"INR\",\"notes\":{\"paymentReference\":\"%s\"}}}}}",
                capturedPaymentRef.get()
        );
        paymentService.processProviderWebhook("MOCK", delayedFailedWebhook, Map.of("x-razorpay-event-id", "evt_late_fail_" + UUID.randomUUID()));

        RestaurantPayment persisted = paymentRepository.findByIdAndTenantId(result.id(), tenantId).orElseThrow();
        assertEquals(PaymentStatus.SUCCEEDED, persisted.getPaymentStatus());
    }

    @Test
    void partialPaymentsAndOverpaymentPreventionWorkCorrectly() {
        RestaurantBill bill = createBill(new BigDecimal("500.00"));

        // Two partial payments: 200.00 + 300.00
        PaymentResponse p1 = paymentService.initiatePayment(staffPrincipal,
                new InitiatePaymentRequest(bill.getId(), PaymentMethod.ONLINE_PAYMENT, PaymentProviderType.MOCK,
                        new BigDecimal("200.00"), "Split 1", null));
        paymentService.settlePayment(staffPrincipal, p1.id(), new VerifyPaymentRequest("txn_split_1", p1.providerOrderId(), "VALID_SIG"));

        RestaurantBill afterFirst = billRepository.findByIdAndTenantId(bill.getId(), tenantId).orElseThrow();
        assertEquals(BillStatus.PARTIALLY_PAID, afterFirst.getStatus());
        assertEquals(0, new BigDecimal("300.00").compareTo(afterFirst.getOutstandingBalance()));

        // Two competing 300.00 attempts initiated while 300.00 is still outstanding
        PaymentResponse p2a = paymentService.initiatePayment(staffPrincipal,
                new InitiatePaymentRequest(bill.getId(), PaymentMethod.ONLINE_PAYMENT, PaymentProviderType.MOCK,
                        new BigDecimal("300.00"), "Split 2A", null));
        PaymentResponse p2b = paymentService.initiatePayment(staffPrincipal,
                new InitiatePaymentRequest(bill.getId(), PaymentMethod.ONLINE_PAYMENT, PaymentProviderType.MOCK,
                        new BigDecimal("300.00"), "Split 2B", null));

        paymentService.settlePayment(staffPrincipal, p2a.id(), new VerifyPaymentRequest("txn_split_2a", p2a.providerOrderId(), "VALID_SIG"));

        // Settling p2b must be blocked by overpayment protection
        BusinessException overpayEx = assertThrows(BusinessException.class,
                () -> paymentService.settlePayment(staffPrincipal, p2b.id(), new VerifyPaymentRequest("txn_split_2b", p2b.providerOrderId(), "VALID_SIG")));
        assertEquals(HttpStatus.CONFLICT, overpayEx.getStatus());
        assertEquals("OVERPAYMENT_PREVENTED", overpayEx.getErrorCode());

        RestaurantBill settledBill = billRepository.findByIdAndTenantId(bill.getId(), tenantId).orElseThrow();
        assertEquals(BillStatus.PAID, settledBill.getStatus());
        assertEquals(0, new BigDecimal("500.00").compareTo(settledBill.getPaidAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(settledBill.getOutstandingBalance()));
    }

    @Test
    void concurrentDuplicateInitiationsWithSameIdempotencyKeyProduceSinglePayment() throws Exception {
        RestaurantBill bill = createBill(new BigDecimal("350.00"));
        String sharedIdemKey = "concurrent-idem-" + UUID.randomUUID();
        InitiatePaymentRequest req = new InitiatePaymentRequest(
                bill.getId(), PaymentMethod.ONLINE_PAYMENT, PaymentProviderType.MOCK,
                new BigDecimal("350.00"), "Concurrent click", null
        );

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<PaymentResponse> task = () -> paymentService.initiatePayment(staffPrincipal, req, sharedIdemKey);
            Future<PaymentResponse> f1 = pool.submit(task);
            Future<PaymentResponse> f2 = pool.submit(task);

            PaymentResponse r1 = f1.get();
            PaymentResponse r2 = f2.get();
            assertNotNull(r1.id());
            assertEquals(r1.id(), r2.id(), "Concurrent duplicate checkout requests with same Idempotency-Key must resolve to one payment");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void stateMachineForbidsDowngradingConfirmedPaymentsAndEnforcesTenantBranchIsolation() {
        assertFalse(PaymentStateMachine.canTransition(PaymentStatus.SUCCEEDED, PaymentStatus.FAILED));
        assertFalse(PaymentStateMachine.canTransition(PaymentStatus.SUCCEEDED, PaymentStatus.CANCELLED));
        assertFalse(PaymentStateMachine.canTransition(PaymentStatus.SUCCEEDED, PaymentStatus.PENDING_CONFIRMATION));
        assertFalse(PaymentStateMachine.canTransition(PaymentStatus.REFUNDED, PaymentStatus.SUCCEEDED));
        assertTrue(PaymentStateMachine.canTransition(PaymentStatus.PENDING_CONFIRMATION, PaymentStatus.SUCCEEDED));
        assertTrue(PaymentStateMachine.canTransition(PaymentStatus.SUCCEEDED, PaymentStatus.PARTIALLY_REFUNDED));

        RestaurantBill bill = createBill(new BigDecimal("220.00"));

        // Cross-tenant isolation
        DineFlowPrincipal otherTenantStaff = new DineFlowPrincipal(
                UUID.randomUUID(), UUID.randomUUID(), branchId, "CASHIER",
                List.of("PAYMENT_PROCESS"), "STAFF"
        );
        BusinessException tenantEx = assertThrows(BusinessException.class, () ->
                paymentService.initiatePayment(otherTenantStaff,
                        new InitiatePaymentRequest(bill.getId(), PaymentMethod.ONLINE_PAYMENT, PaymentProviderType.MOCK,
                                new BigDecimal("220.00"), null, null)));
        assertEquals(HttpStatus.NOT_FOUND, tenantEx.getStatus());

        // Cross-branch isolation
        DineFlowPrincipal otherBranchStaff = new DineFlowPrincipal(
                UUID.randomUUID(), tenantId, UUID.randomUUID(), "CASHIER",
                List.of("PAYMENT_PROCESS"), "STAFF"
        );
        BusinessException branchEx = assertThrows(BusinessException.class, () ->
                paymentService.initiatePayment(otherBranchStaff,
                        new InitiatePaymentRequest(bill.getId(), PaymentMethod.ONLINE_PAYMENT, PaymentProviderType.MOCK,
                                new BigDecimal("220.00"), null, null)));
        assertEquals(HttpStatus.FORBIDDEN, branchEx.getStatus());
    }
}
