package com.dineflow.payment;

import com.dineflow.billing.domain.BillStatus;
import com.dineflow.billing.domain.RestaurantBill;
import com.dineflow.billing.repository.RestaurantBillRepository;
import com.dineflow.payment.domain.PaymentAttempt;
import com.dineflow.payment.domain.PaymentMethod;
import com.dineflow.payment.domain.PaymentProviderType;
import com.dineflow.payment.domain.PaymentStatus;
import com.dineflow.payment.domain.RefundMethod;
import com.dineflow.payment.domain.RefundStatus;
import com.dineflow.payment.domain.RestaurantPayment;
import com.dineflow.payment.domain.RestaurantRefund;
import com.dineflow.payment.dto.ReconciliationReport;
import com.dineflow.payment.provider.PaymentGatewayProvider;
import com.dineflow.payment.repository.PaymentAttemptRepository;
import com.dineflow.payment.repository.PaymentReconciliationLogRepository;
import com.dineflow.payment.repository.PaymentWebhookEventRepository;
import com.dineflow.payment.repository.RestaurantPaymentRepository;
import com.dineflow.payment.repository.RestaurantRefundRepository;
import com.dineflow.payment.service.PaymentProcessingService;
import com.dineflow.payment.service.PaymentReferenceGenerator;
import com.dineflow.payment.service.ReconciliationOperationsService;
import com.dineflow.payment.service.ReconciliationRetryPolicy;
import com.dineflow.payment.service.RefundManagementService;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class DistributedSchedulingAndReconciliationTests {

    @Autowired
    private LockProvider lockProvider;

    @Autowired
    private RestaurantBillRepository billRepository;

    @Autowired
    private RestaurantPaymentRepository paymentRepository;

    @Autowired
    private PaymentAttemptRepository attemptRepository;

    @Autowired
    private RestaurantRefundRepository refundRepository;

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

    @Autowired
    private ReconciliationOperationsService operationsService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void ensureShedLockTableInH2TestDb() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS shedlock (
                    name VARCHAR(64) NOT NULL PRIMARY KEY,
                    lock_until TIMESTAMP WITH TIME ZONE NOT NULL,
                    locked_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    locked_by VARCHAR(255) NOT NULL
                )
                """);
    }

    @Test
    void realShedLockAllowsOnlyOneReplicaAtATimeAndRecoversAfterCrashExpiry() throws Exception {
        String lockName = "testReconLock-" + UUID.randomUUID().toString().substring(0, 8);
        DefaultLockingTaskExecutor replica1 = new DefaultLockingTaskExecutor(lockProvider);
        DefaultLockingTaskExecutor replica2 = new DefaultLockingTaskExecutor(lockProvider);

        AtomicInteger concurrentExecutions = new AtomicInteger(0);
        CountDownLatch insideLock = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            pool.submit(() -> replica1.executeWithLock((Runnable) () -> {
                concurrentExecutions.incrementAndGet();
                insideLock.countDown();
                try {
                    releaseLock.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }
            }, new LockConfiguration(Instant.now(), lockName, Duration.ofSeconds(10), Duration.ofSeconds(5))));

            assertTrue(insideLock.await(3, TimeUnit.SECONDS));

            // Replica 2 attempts to acquire the exact same lock while Replica 1 holds it -> must NOT execute
            replica2.executeWithLock((Runnable) concurrentExecutions::incrementAndGet,
                    new LockConfiguration(Instant.now(), lockName, Duration.ofSeconds(10), Duration.ofSeconds(5)));

            assertEquals(1, concurrentExecutions.get(), "Only one replica may execute while ShedLock is held");
            releaseLock.countDown();
        } finally {
            pool.shutdownNow();
        }

        // Verify operational lock status endpoint reads live shedlock table
        List<ReconciliationOperationsService.SchedulerLockStatus> locks = operationsService.getSchedulerLocks();
        assertTrue(locks.stream().anyMatch(l -> lockName.equals(l.name())));

        // Crash & lock-expiry recovery test: short lockAtMostFor=150ms simulates crashed node
        String crashLock = "crashLock-" + UUID.randomUUID().toString().substring(0, 8);
        AtomicInteger crashRuns = new AtomicInteger(0);
        try {
            replica1.executeWithLock((Runnable) () -> {
                crashRuns.incrementAndGet();
                throw new RuntimeException("Simulated pod OOM / crash");
            }, new LockConfiguration(Instant.now(), crashLock, Duration.ofMillis(150), Duration.ofMillis(50)));
        } catch (RuntimeException expected) {
            // Expected crash
        }
        Thread.sleep(200); // Wait for lock expiry
        replica2.executeWithLock((Runnable) crashRuns::incrementAndGet,
                new LockConfiguration(Instant.now(), crashLock, Duration.ofSeconds(2), Duration.ofMillis(50)));
        assertEquals(2, crashRuns.get(), "Replica 2 must recover and run after crashed replica's lock expires");
    }

    @Test
    void retryBackoffExhaustionFlagsManualReviewAndDuplicateReconciliationIsIdempotent() {
        UUID tenantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        RestaurantBill bill = new RestaurantBill(tenantId, branchId, null, "B-REC-" + UUID.randomUUID().toString().substring(0, 6), "T-5", "INR");
        bill.setSubtotal(new BigDecimal("500.00"));
        bill.setGrandTotal(new BigDecimal("500.00"));
        bill.setOutstandingBalance(new BigDecimal("500.00"));
        bill.setStatus(BillStatus.ISSUED);
        bill = billRepository.save(bill);

        RestaurantPayment payment = new RestaurantPayment(
                tenantId, branchId, bill, "PAY-REC-" + UUID.randomUUID().toString().substring(0, 6),
                PaymentMethod.ONLINE_PAYMENT, new BigDecimal("500.00"), "INR", PaymentProviderType.MOCK
        );
        payment.setPaymentStatus(PaymentStatus.PENDING_CONFIRMATION);
        PaymentAttempt attempt = new PaymentAttempt(payment, tenantId, 1, "idem-rec-" + UUID.randomUUID(), "MOCK", new BigDecimal("500.00"), "INR");
        attempt.setProviderOrderId("ord_rec_exhaust_1");
        attempt.setAmbiguousOutcome(true);
        payment.addAttempt(attempt);
        final RestaurantPayment savedPayment = paymentRepository.save(payment);

        // Provider persistently unavailable (transientError = true)
        PaymentGatewayProvider unavailableProvider = new StubGatewayProvider(
                new PaymentGatewayProvider.GatewayVerificationResult(false, null, null, "INR", "POLL_HTTP_503", "Service Unavailable", true),
                null
        );
        PaymentProcessingService reconService = new PaymentProcessingService(
                paymentRepository, attemptRepository, billRepository, webhookEventRepository,
                reconciliationLogRepository, referenceGenerator, List.of(unavailableProvider),
                transactionTemplate, eventPublisher
        );

        // Run #1 -> transient error schedules nextReconciliationAt in future
        ReconciliationReport r1 = reconService.reconcilePendingPayments(tenantId, "SCHEDULER");
        assertEquals(1, r1.discrepancyCount());

        PaymentAttempt afterFirst = transactionTemplate.execute(s ->
                paymentRepository.findByIdAndTenantId(savedPayment.getId(), tenantId).orElseThrow().getAttempts().get(0));
        assertEquals(1, afterFirst.getReconciliationRetryCount());
        assertNotNull(afterFirst.getNextReconciliationAt());
        assertFalse(afterFirst.isManualReviewRequired());

        // Immediate Run #2 during backoff cooldown -> skipped (scanned == 0)
        ReconciliationReport duringCooldown = reconService.reconcilePendingPayments(tenantId, "SCHEDULER");
        assertEquals(0, duringCooldown.scannedCount(), "Payment in exponential backoff cooldown must not be hammered");

        // Advance retry count to MAX_RETRIES - 1 and clear cooldown -> next failure exhausts retries -> manualReviewRequired = true
        transactionTemplate.executeWithoutResult(s -> {
            RestaurantPayment p = paymentRepository.findByIdAndTenantId(savedPayment.getId(), tenantId).orElseThrow();
            PaymentAttempt a = p.getAttempts().get(0);
            a.setReconciliationRetryCount(ReconciliationRetryPolicy.DEFAULT_MAX_RETRIES - 1);
            a.setNextReconciliationAt(Instant.now().minusSeconds(10));
            paymentRepository.save(p);
        });

        reconService.reconcilePendingPayments(tenantId, "SCHEDULER");
        PaymentAttempt exhausted = transactionTemplate.execute(s ->
                paymentRepository.findByIdAndTenantId(savedPayment.getId(), tenantId).orElseThrow().getAttempts().get(0));
        assertTrue(exhausted.isManualReviewRequired(), "Exhausted retries must transition to manual-review dead-letter state");
        assertNull(exhausted.getNextReconciliationAt());

        // Now test idempotent settlement recovery when provider confirms captured
        transactionTemplate.executeWithoutResult(s -> {
            RestaurantPayment p = paymentRepository.findByIdAndTenantId(savedPayment.getId(), tenantId).orElseThrow();
            p.getAttempts().get(0).setManualReviewRequired(false);
            paymentRepository.save(p);
        });

        PaymentGatewayProvider capturedProvider = new StubGatewayProvider(
                new PaymentGatewayProvider.GatewayVerificationResult(true, "pay_ok_777", new BigDecimal("500.00"), "INR", null, null, false),
                null
        );
        PaymentProcessingService recoveringService = new PaymentProcessingService(
                paymentRepository, attemptRepository, billRepository, webhookEventRepository,
                reconciliationLogRepository, referenceGenerator, List.of(capturedProvider),
                transactionTemplate, eventPublisher
        );

        ReconciliationReport ok1 = recoveringService.reconcilePendingPayments(tenantId, "MANUAL_STAFF:ops-1");
        ReconciliationReport ok2 = recoveringService.reconcilePendingPayments(tenantId, "MANUAL_STAFF:ops-1");
        assertEquals(1, ok1.resolvedCount());
        assertEquals(0, ok2.resolvedCount(), "Duplicate reconciliation execution must never double-settle");

        RestaurantBill settledBill = billRepository.findByIdAndTenantId(bill.getId(), tenantId).orElseThrow();
        assertEquals(BillStatus.PAID, settledBill.getStatus());
        assertEquals(0, new BigDecimal("500.00").compareTo(settledBill.getPaidAmount()));

        // Tenant-isolated operational history check
        List<ReconciliationOperationsService.JobExecutionView> history = operationsService.getRecentExecutions(tenantId);
        assertFalse(history.isEmpty());
        assertTrue(history.stream().anyMatch(h -> "MANUAL_STAFF:ops-1".equals(h.triggeredBy())));
        assertTrue(operationsService.getRecentExecutions(UUID.randomUUID()).isEmpty(), "Other tenant must not see Tenant A's reconciliation logs");
    }

    @Test
    void refundReconciliationResolvesInitiatedRefundWithoutDoubleRefunding() {
        UUID tenantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        RestaurantBill bill = new RestaurantBill(tenantId, branchId, null, "B-REF-" + UUID.randomUUID().toString().substring(0, 6), "T-9", "INR");
        bill.setSubtotal(new BigDecimal("400.00"));
        bill.setGrandTotal(new BigDecimal("400.00"));
        bill.setPaidAmount(new BigDecimal("400.00"));
        bill.setOutstandingBalance(BigDecimal.ZERO);
        bill.setStatus(BillStatus.PAID);
        bill = billRepository.save(bill);

        RestaurantPayment payment = new RestaurantPayment(
                tenantId, branchId, bill, "PAY-REF-" + UUID.randomUUID().toString().substring(0, 6),
                PaymentMethod.ONLINE_PAYMENT, new BigDecimal("400.00"), "INR", PaymentProviderType.MOCK
        );
        payment.setPaidAmount(new BigDecimal("400.00"));
        payment.setPaymentStatus(PaymentStatus.SUCCEEDED);
        payment.setProviderPaymentId("pay_orig_123");
        payment = paymentRepository.save(payment);

        RestaurantRefund refund = new RestaurantRefund(
                tenantId, branchId, payment, bill, "REF-REC-" + UUID.randomUUID().toString().substring(0, 6),
                new BigDecimal("150.00"), "INR", RefundMethod.ONLINE_GATEWAY, "Partial cancellation", UUID.randomUUID()
        );
        refund.setRefundStatus(RefundStatus.INITIATED);
        refund.setProviderRefundId("rfnd_provider_123");
        refund = refundRepository.save(refund);

        PaymentGatewayProvider refundConfirmedProvider = new StubGatewayProvider(
                null,
                new PaymentGatewayProvider.GatewayRefundResult(true, "rfnd_provider_123", new BigDecimal("150.00"), null, null, false)
        );
        RefundManagementService refundReconService = new RefundManagementService(
                refundRepository, paymentRepository, billRepository, reconciliationLogRepository,
                referenceGenerator, List.of(refundConfirmedProvider), transactionTemplate
        );

        ReconciliationReport first = refundReconService.reconcileInitiatedRefunds(tenantId, "SCHEDULER");
        ReconciliationReport second = refundReconService.reconcileInitiatedRefunds(tenantId, "SCHEDULER");
        assertEquals(1, first.resolvedCount());
        assertEquals(0, second.resolvedCount(), "Duplicate refund reconciliation must not double-refund");

        RestaurantRefund updatedRefund = refundRepository.findByIdAndTenantId(refund.getId(), tenantId).orElseThrow();
        assertEquals(RefundStatus.SUCCEEDED, updatedRefund.getRefundStatus());

        RestaurantPayment updatedPayment = paymentRepository.findByIdAndTenantId(payment.getId(), tenantId).orElseThrow();
        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, updatedPayment.getPaymentStatus());
        assertEquals(0, new BigDecimal("150.00").compareTo(updatedPayment.getRefundedAmount()));

        // Clean up refund row so subsequent test classes doing paymentRepository.deleteAll() do not hit FK violation
        refundRepository.deleteById(updatedRefund.getId());
    }

    private record StubGatewayProvider(
            PaymentGatewayProvider.GatewayVerificationResult orderPollResult,
            PaymentGatewayProvider.GatewayRefundResult refundPollResult
    ) implements PaymentGatewayProvider {
        @Override public PaymentProviderType getProviderType() { return PaymentProviderType.MOCK; }
        @Override public GatewayOrderResult createPaymentOrder(RestaurantBill b, RestaurantPayment p, PaymentAttempt a, String cb) {
            return new GatewayOrderResult(true, "ord_stub", "sec", null, null, null, "{}");
        }
        @Override public GatewayVerificationResult verifyPayment(String o, String t, String s) {
            return new GatewayVerificationResult(true, t, null, "INR", null, null);
        }
        @Override public GatewayVerificationResult verifyWebhook(String r, Map<String, String> h) {
            return new GatewayVerificationResult(true, "txn", null, "INR", null, "payment.captured");
        }
        @Override public GatewayVerificationResult fetchOrderStatus(String providerOrderId) {
            return orderPollResult;
        }
        @Override public GatewayRefundResult processRefund(RestaurantPayment p, BigDecimal a, String r) {
            return refundPollResult;
        }
        @Override public GatewayRefundResult fetchRefundStatus(String providerPaymentId, String providerRefundId) {
            return refundPollResult;
        }
    }
}
