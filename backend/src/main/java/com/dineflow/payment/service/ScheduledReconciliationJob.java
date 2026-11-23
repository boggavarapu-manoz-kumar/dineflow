package com.dineflow.payment.service;

import com.dineflow.config.CorrelationIdFilter;
import com.dineflow.payment.dto.ReconciliationReport;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Multi-replica safe scheduled financial reconciliation jobs protected by database-backed ShedLock.
 * Every job is idempotent and bounded-batch so repeated execution or crash recovery never creates
 * duplicate settlements or duplicate refunds.
 */
@Component
@ConditionalOnProperty(name = "dineflow.reconciliation.enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(ScheduledReconciliationJob.class);

    private final PaymentProcessingService paymentProcessingService;
    private final RefundManagementService refundManagementService;
    private final MeterRegistry meterRegistry;

    @Autowired
    public ScheduledReconciliationJob(PaymentProcessingService paymentProcessingService,
                                      RefundManagementService refundManagementService,
                                      @Autowired(required = false) MeterRegistry meterRegistry) {
        this.paymentProcessingService = paymentProcessingService;
        this.refundManagementService = refundManagementService;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(cron = "${dineflow.reconciliation.cron:0 */5 * * * *}")
    @SchedulerLock(name = "paymentReconciliationJob", lockAtLeastFor = "PT30S", lockAtMostFor = "PT4M")
    public void runPeriodicReconciliation() {
        MDC.put(CorrelationIdFilter.MDC_KEY, CorrelationIdFilter.currentOrNew("job-pay-recon"));
        long startNanos = System.nanoTime();
        log.info("Starting ShedLock-guarded scheduled payment reconciliation...");
        try {
            ReconciliationReport report = paymentProcessingService.reconcilePendingPayments(null, "SCHEDULER");
            recordMetrics("payment", "completed", System.nanoTime() - startNanos, report);
            log.info("Scheduled payment reconciliation completed: scanned={}, resolved={}, timedOut={}, discrepancies={}",
                    report.scannedCount(), report.resolvedCount(), report.timedOutCount(), report.discrepancyCount());
        } catch (Exception ex) {
            recordMetrics("payment", "failed", System.nanoTime() - startNanos, null);
            log.error("Scheduled payment reconciliation failed (lock will auto-release safely): {}", ex.getMessage(), ex);
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    @Scheduled(cron = "${dineflow.reconciliation.refund-cron:30 */5 * * * *}")
    @SchedulerLock(name = "refundReconciliationJob", lockAtLeastFor = "PT30S", lockAtMostFor = "PT4M")
    public void runPeriodicRefundReconciliation() {
        MDC.put(CorrelationIdFilter.MDC_KEY, CorrelationIdFilter.currentOrNew("job-ref-recon"));
        long startNanos = System.nanoTime();
        log.info("Starting ShedLock-guarded scheduled refund reconciliation...");
        try {
            ReconciliationReport report = refundManagementService.reconcileInitiatedRefunds(null, "SCHEDULER");
            recordMetrics("refund", "completed", System.nanoTime() - startNanos, report);
            log.info("Scheduled refund reconciliation completed: scanned={}, resolved={}, failed={}, discrepancies={}",
                    report.scannedCount(), report.resolvedCount(), report.timedOutCount(), report.discrepancyCount());
        } catch (Exception ex) {
            recordMetrics("refund", "failed", System.nanoTime() - startNanos, null);
            log.error("Scheduled refund reconciliation failed (lock will auto-release safely): {}", ex.getMessage(), ex);
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    private void recordMetrics(String type, String status, long elapsedNanos, ReconciliationReport report) {
        if (meterRegistry == null) return;
        meterRegistry.counter("dineflow.reconciliation.runs", "type", type, "status", status).increment();
        Timer.builder("dineflow.reconciliation.duration")
                .tag("type", type)
                .tag("status", status)
                .register(meterRegistry)
                .record(elapsedNanos, TimeUnit.NANOSECONDS);
        if (report != null) {
            meterRegistry.counter("dineflow.reconciliation.items", "type", type, "outcome", "resolved").increment(report.resolvedCount());
            meterRegistry.counter("dineflow.reconciliation.items", "type", type, "outcome", "discrepancy").increment(report.discrepancyCount());
            meterRegistry.counter("dineflow.reconciliation.items", "type", type, "outcome", "timed_out").increment(report.timedOutCount());
        }
    }
}
