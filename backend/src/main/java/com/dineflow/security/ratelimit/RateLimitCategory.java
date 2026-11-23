package com.dineflow.security.ratelimit;

public enum RateLimitCategory {
    AUTH_LOGIN("auth-login", FailurePolicy.LOCAL_FALLBACK),
    GUEST_SESSION("guest-session", FailurePolicy.LOCAL_FALLBACK),
    QR_VALIDATION("qr-validation", FailurePolicy.LOCAL_FALLBACK),
    PAYMENT_INITIATE("payment-initiate", FailurePolicy.LOCAL_FALLBACK),
    COUPON_REDEEM("coupon-redeem", FailurePolicy.LOCAL_FALLBACK),
    REFUND_REQUEST("refund-request", FailurePolicy.LOCAL_FALLBACK),
    PUBLIC_MENU("public-menu", FailurePolicy.FAIL_OPEN),
    MEDIA_DOCUMENT("media-document", FailurePolicy.FAIL_OPEN),
    WEBHOOK("webhook", FailurePolicy.FAIL_OPEN);

    public enum FailurePolicy {
        FAIL_OPEN,
        FAIL_CLOSED,
        LOCAL_FALLBACK
    }

    private final String keySegment;
    private final FailurePolicy defaultFailurePolicy;

    RateLimitCategory(String keySegment, FailurePolicy defaultFailurePolicy) {
        this.keySegment = keySegment;
        this.defaultFailurePolicy = defaultFailurePolicy;
    }

    public String getKeySegment() {
        return keySegment;
    }

    public FailurePolicy getDefaultFailurePolicy() {
        return defaultFailurePolicy;
    }
}
