package com.dineflow.observability;

import com.dineflow.order.domain.OrderStatus;
import com.dineflow.order.repository.RestaurantOrderRepository;
import com.dineflow.payment.domain.PaymentStatus;
import com.dineflow.payment.repository.RestaurantPaymentRepository;
import com.dineflow.realtime.RealtimeOutboxEvent;
import com.dineflow.realtime.RealtimeOutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Centralized low-cardinality business & operational Micrometer metrics for DineFlow.
 * Never attaches high-cardinality PII or tenant/user/order IDs as metric tags.
 */
@Service
public class DineFlowMetricsService {

    private final MeterRegistry meterRegistry;

    public DineFlowMetricsService(MeterRegistry meterRegistry,
                                  RealtimeOutboxEventRepository outboxRepository,
                                  RestaurantPaymentRepository paymentRepository,
                                  RestaurantOrderRepository orderRepository) {
        this.meterRegistry = meterRegistry;

        Gauge.builder("dineflow.outbox.backlog", outboxRepository,
                        repo -> repo.countByStatus(RealtimeOutboxEvent.STATUS_PENDING))
                .tag("status", "pending")
                .description("Number of uncommitted/pending realtime outbox events awaiting publication")
                .register(meterRegistry);

        Gauge.builder("dineflow.outbox.backlog", outboxRepository,
                        repo -> repo.countByStatus(RealtimeOutboxEvent.STATUS_DEAD_LETTER))
                .tag("status", "dead_letter")
                .description("Number of realtime outbox events that exhausted retries")
                .register(meterRegistry);

        Gauge.builder("dineflow.reconciliation.pending_confirmation", paymentRepository,
                        repo -> repo.countByPaymentStatus(PaymentStatus.PENDING_CONFIRMATION))
                .description("Number of payments in ambiguous PENDING_CONFIRMATION state awaiting reconciliation")
                .register(meterRegistry);

        Gauge.builder("dineflow.kitchen.active_queue", orderRepository,
                        repo -> repo.countByStatusIn(List.of(OrderStatus.ACCEPTED, OrderStatus.PREPARING)))
                .description("Current number of active kitchen orders in ACCEPTED or PREPARING state")
                .register(meterRegistry);
    }

    public void recordPaymentInitiation(String provider, String method, String outcome) {
        meterRegistry.counter("dineflow.payments.initiations",
                "provider", safeTag(provider),
                "method", safeTag(method),
                "outcome", safeTag(outcome)).increment();
    }

    public void recordPaymentSettlement(String method, String source) {
        meterRegistry.counter("dineflow.payments.settlements",
                "method", safeTag(method),
                "source", safeTag(source)).increment();
    }

    public void recordWebhook(String provider, String outcome) {
        meterRegistry.counter("dineflow.payments.webhooks",
                "provider", safeTag(provider),
                "outcome", safeTag(outcome)).increment();
    }

    public void recordRefundExecution(String method, String outcome) {
        meterRegistry.counter("dineflow.refunds.executions",
                "method", safeTag(method),
                "outcome", safeTag(outcome)).increment();
    }

    public void recordOrderTransition(String targetStatus, String outcome) {
        meterRegistry.counter("dineflow.orders.transitions",
                "status", safeTag(targetStatus),
                "outcome", safeTag(outcome)).increment();
    }

    public void recordStorageOperation(String backend, String operation, String outcome) {
        meterRegistry.counter("dineflow.storage.operations",
                "backend", safeTag(backend),
                "operation", safeTag(operation),
                "outcome", safeTag(outcome)).increment();
    }

    private static String safeTag(String value) {
        return (value == null || value.isBlank()) ? "unknown" : value.toLowerCase();
    }
}
