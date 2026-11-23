package com.dineflow.payment.domain;

public enum PaymentStatus {
    CREATED,
    PENDING,
    INITIATED,
    PROCESSING,
    PENDING_CONFIRMATION,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    PARTIALLY_REFUNDED,
    REFUNDED
}
