package com.dineflow.payment.domain;

import com.dineflow.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Authoritative state machine for {@link RestaurantPayment} lifecycle transitions.
 * Enforces monotonic financial state progression so a confirmed payment
 * (SUCCEEDED, PARTIALLY_REFUNDED, REFUNDED) can never be downgraded by a delayed
 * failure event, timeout, or out-of-order webhook.
 */
public final class PaymentStateMachine {

    private static final Set<PaymentStatus> CONFIRMED_OR_REFUNDED = EnumSet.of(
            PaymentStatus.SUCCEEDED,
            PaymentStatus.PARTIALLY_REFUNDED,
            PaymentStatus.REFUNDED
    );

    private static final Set<PaymentStatus> RECOVERABLE_PENDING = EnumSet.of(
            PaymentStatus.CREATED,
            PaymentStatus.PENDING,
            PaymentStatus.INITIATED,
            PaymentStatus.PROCESSING,
            PaymentStatus.PENDING_CONFIRMATION
    );

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(PaymentStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(PaymentStatus.CREATED, EnumSet.of(
                PaymentStatus.INITIATED, PaymentStatus.PENDING, PaymentStatus.PENDING_CONFIRMATION,
                PaymentStatus.SUCCEEDED, PaymentStatus.FAILED, PaymentStatus.CANCELLED
        ));
        ALLOWED_TRANSITIONS.put(PaymentStatus.PENDING, EnumSet.of(
                PaymentStatus.INITIATED, PaymentStatus.PROCESSING, PaymentStatus.PENDING_CONFIRMATION,
                PaymentStatus.SUCCEEDED, PaymentStatus.FAILED, PaymentStatus.CANCELLED
        ));
        ALLOWED_TRANSITIONS.put(PaymentStatus.INITIATED, EnumSet.of(
                PaymentStatus.PROCESSING, PaymentStatus.PENDING_CONFIRMATION,
                PaymentStatus.SUCCEEDED, PaymentStatus.FAILED, PaymentStatus.CANCELLED
        ));
        ALLOWED_TRANSITIONS.put(PaymentStatus.PROCESSING, EnumSet.of(
                PaymentStatus.PENDING_CONFIRMATION, PaymentStatus.SUCCEEDED,
                PaymentStatus.FAILED, PaymentStatus.CANCELLED
        ));
        ALLOWED_TRANSITIONS.put(PaymentStatus.PENDING_CONFIRMATION, EnumSet.of(
                PaymentStatus.SUCCEEDED, PaymentStatus.FAILED, PaymentStatus.CANCELLED
        ));
        // Confirmed payments may ONLY advance to refund states, never backward
        ALLOWED_TRANSITIONS.put(PaymentStatus.SUCCEEDED, EnumSet.of(
                PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED
        ));
        ALLOWED_TRANSITIONS.put(PaymentStatus.PARTIALLY_REFUNDED, EnumSet.of(
                PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED
        ));
        ALLOWED_TRANSITIONS.put(PaymentStatus.REFUNDED, EnumSet.noneOf(PaymentStatus.class));
        // Late webhook capture recovery after premature local failure/cancellation is explicitly allowed
        // ONLY when authoritative provider settlement arrives via verified webhook/reconciliation
        ALLOWED_TRANSITIONS.put(PaymentStatus.FAILED, EnumSet.of(PaymentStatus.SUCCEEDED));
        ALLOWED_TRANSITIONS.put(PaymentStatus.CANCELLED, EnumSet.of(PaymentStatus.SUCCEEDED));
    }

    private PaymentStateMachine() {
    }

    public static boolean isConfirmedOrRefunded(PaymentStatus status) {
        return status != null && CONFIRMED_OR_REFUNDED.contains(status);
    }

    public static boolean isRecoverable(PaymentStatus status) {
        return status != null && RECOVERABLE_PENDING.contains(status);
    }

    public static boolean canTransition(PaymentStatus from, PaymentStatus to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return true;
        }
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void assertValidTransition(PaymentStatus from, PaymentStatus to) {
        if (!canTransition(from, to)) {
            throw new BusinessException(
                    String.format("Invalid payment state transition from %s to %s", from, to),
                    HttpStatus.CONFLICT,
                    "INVALID_PAYMENT_STATE_TRANSITION"
            );
        }
    }
}
