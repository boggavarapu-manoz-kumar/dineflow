package com.dineflow.payment.domain;

public enum RefundStatus {
    PENDING_APPROVAL,
    APPROVED,
    INITIATED,
    SUCCEEDED,
    FAILED,
    REJECTED
}
