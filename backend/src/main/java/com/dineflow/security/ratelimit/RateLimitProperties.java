package com.dineflow.security.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "dineflow.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private String store = "memory"; // memory | redis
    private boolean failOpen = true;
    private boolean strictFailClosedOnSensitive = false;
    private int maxInMemoryBuckets = 50_000;

    private int authLimitPerMinute = 15;
    private int guestSessionLimitPerMinute = 40;
    private int qrValidationLimitPerMinute = 60;
    private int paymentInitiateLimitPerMinute = 20;
    private int couponLimitPerMinute = 25;
    private int refundRequestLimitPerMinute = 15;
    private int publicMenuLimitPerMinute = 120;
    private int mediaDocumentLimitPerMinute = 120;
    private int webhookLimitPerMinute = 300;

    private List<String> trustedProxies = new ArrayList<>(List.of(
            "127.0.0.1", "::1", "0:0:0:0:0:0:0:1", "10.", "172.16.", "172.17.", "172.18.", "172.19.",
            "172.20.", "172.21.", "172.22.", "172.23.", "172.24.", "172.25.", "172.26.", "172.27.",
            "172.28.", "172.29.", "172.30.", "172.31.", "192.168."
    ));

    public int limitFor(RateLimitCategory category) {
        return switch (category) {
            case AUTH_LOGIN -> authLimitPerMinute;
            case GUEST_SESSION -> guestSessionLimitPerMinute;
            case QR_VALIDATION -> qrValidationLimitPerMinute;
            case PAYMENT_INITIATE -> paymentInitiateLimitPerMinute;
            case COUPON_REDEEM -> couponLimitPerMinute;
            case REFUND_REQUEST -> refundRequestLimitPerMinute;
            case PUBLIC_MENU -> publicMenuLimitPerMinute;
            case MEDIA_DOCUMENT -> mediaDocumentLimitPerMinute;
            case WEBHOOK -> webhookLimitPerMinute;
        };
    }

    public RateLimitCategory.FailurePolicy failurePolicyFor(RateLimitCategory category) {
        if (category == null) {
            return failOpen ? RateLimitCategory.FailurePolicy.FAIL_OPEN : RateLimitCategory.FailurePolicy.FAIL_CLOSED;
        }
        RateLimitCategory.FailurePolicy base = category.getDefaultFailurePolicy();
        if (base == RateLimitCategory.FailurePolicy.LOCAL_FALLBACK && strictFailClosedOnSensitive) {
            return RateLimitCategory.FailurePolicy.FAIL_CLOSED;
        }
        return base;
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getStore() { return store; }
    public void setStore(String store) { this.store = store; }
    public boolean isFailOpen() { return failOpen; }
    public void setFailOpen(boolean failOpen) { this.failOpen = failOpen; }
    public boolean isStrictFailClosedOnSensitive() { return strictFailClosedOnSensitive; }
    public void setStrictFailClosedOnSensitive(boolean strictFailClosedOnSensitive) { this.strictFailClosedOnSensitive = strictFailClosedOnSensitive; }
    public int getMaxInMemoryBuckets() { return maxInMemoryBuckets; }
    public void setMaxInMemoryBuckets(int maxInMemoryBuckets) { this.maxInMemoryBuckets = maxInMemoryBuckets; }
    public int getAuthLimitPerMinute() { return authLimitPerMinute; }
    public void setAuthLimitPerMinute(int authLimitPerMinute) { this.authLimitPerMinute = authLimitPerMinute; }
    public int getGuestSessionLimitPerMinute() { return guestSessionLimitPerMinute; }
    public void setGuestSessionLimitPerMinute(int guestSessionLimitPerMinute) { this.guestSessionLimitPerMinute = guestSessionLimitPerMinute; }
    public int getQrValidationLimitPerMinute() { return qrValidationLimitPerMinute; }
    public void setQrValidationLimitPerMinute(int qrValidationLimitPerMinute) { this.qrValidationLimitPerMinute = qrValidationLimitPerMinute; }
    public int getPaymentInitiateLimitPerMinute() { return paymentInitiateLimitPerMinute; }
    public void setPaymentInitiateLimitPerMinute(int paymentInitiateLimitPerMinute) { this.paymentInitiateLimitPerMinute = paymentInitiateLimitPerMinute; }
    public int getCouponLimitPerMinute() { return couponLimitPerMinute; }
    public void setCouponLimitPerMinute(int couponLimitPerMinute) { this.couponLimitPerMinute = couponLimitPerMinute; }
    public int getRefundRequestLimitPerMinute() { return refundRequestLimitPerMinute; }
    public void setRefundRequestLimitPerMinute(int refundRequestLimitPerMinute) { this.refundRequestLimitPerMinute = refundRequestLimitPerMinute; }
    public int getPublicMenuLimitPerMinute() { return publicMenuLimitPerMinute; }
    public void setPublicMenuLimitPerMinute(int publicMenuLimitPerMinute) { this.publicMenuLimitPerMinute = publicMenuLimitPerMinute; }
    public int getMediaDocumentLimitPerMinute() { return mediaDocumentLimitPerMinute; }
    public void setMediaDocumentLimitPerMinute(int mediaDocumentLimitPerMinute) { this.mediaDocumentLimitPerMinute = mediaDocumentLimitPerMinute; }
    public int getWebhookLimitPerMinute() { return webhookLimitPerMinute; }
    public void setWebhookLimitPerMinute(int webhookLimitPerMinute) { this.webhookLimitPerMinute = webhookLimitPerMinute; }
    public List<String> getTrustedProxies() { return trustedProxies; }
    public void setTrustedProxies(List<String> trustedProxies) { this.trustedProxies = trustedProxies; }
}
