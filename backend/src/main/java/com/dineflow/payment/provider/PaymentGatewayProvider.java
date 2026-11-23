package com.dineflow.payment.provider;

import com.dineflow.billing.domain.RestaurantBill;
import com.dineflow.payment.domain.PaymentAttempt;
import com.dineflow.payment.domain.PaymentProviderType;
import com.dineflow.payment.domain.RestaurantPayment;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Universal SPI for payment gateway abstraction in DineFlow.
 * Eliminates direct vendor SDK dependencies inside core business services.
 */
public interface PaymentGatewayProvider {

    PaymentProviderType getProviderType();

    record GatewayOrderResult(
            boolean isSuccess,
            String providerOrderId,
            String clientSecret,
            String redirectUrl,
            String errorCode,
            String errorMessage,
            String rawResponse,
            boolean ambiguous
    ) {
        public GatewayOrderResult(boolean isSuccess,
                                  String providerOrderId,
                                  String clientSecret,
                                  String redirectUrl,
                                  String errorCode,
                                  String errorMessage,
                                  String rawResponse) {
            this(isSuccess, providerOrderId, clientSecret, redirectUrl, errorCode, errorMessage, rawResponse, false);
        }
    }

    record GatewayVerificationResult(
            boolean isVerified,
            String providerTransactionId,
            BigDecimal verifiedAmount,
            String currencyCode,
            String errorCode,
            String errorMessage,
            boolean transientError
    ) {
        public GatewayVerificationResult(boolean isVerified,
                                         String providerTransactionId,
                                         BigDecimal verifiedAmount,
                                         String currencyCode,
                                         String errorCode,
                                         String errorMessage) {
            this(isVerified, providerTransactionId, verifiedAmount, currencyCode, errorCode, errorMessage, false);
        }
    }

    record GatewayWebhookResult(
            boolean isVerified,
            String eventType,
            String providerOrderId,
            String providerTransactionId,
            BigDecimal verifiedAmount,
            String currencyCode,
            String errorCode,
            String errorMessage,
            String paymentReference
    ) {
        public GatewayWebhookResult(boolean isVerified,
                                    String eventType,
                                    String providerOrderId,
                                    String providerTransactionId,
                                    BigDecimal verifiedAmount,
                                    String currencyCode,
                                    String errorCode,
                                    String errorMessage) {
            this(isVerified, eventType, providerOrderId, providerTransactionId, verifiedAmount, currencyCode, errorCode, errorMessage, null);
        }
    }

    record GatewayRefundResult(
            boolean isSuccess,
            String providerRefundId,
            BigDecimal refundedAmount,
            String errorCode,
            String errorMessage,
            boolean ambiguous
    ) {
        public GatewayRefundResult(boolean isSuccess,
                                   String providerRefundId,
                                   BigDecimal refundedAmount,
                                   String errorCode,
                                   String errorMessage) {
            this(isSuccess, providerRefundId, refundedAmount, errorCode, errorMessage, false);
        }
    }

    /**
     * Initializes an order/session with the upstream payment provider.
     */
    GatewayOrderResult createPaymentOrder(RestaurantBill bill, RestaurantPayment payment, PaymentAttempt attempt, String callbackUrl);

    /**
     * Verifies a provider payment completion callback using signature validation.
     */
    GatewayVerificationResult verifyPayment(String providerOrderId, String providerTransactionId, String signaturePayload);

    /**
     * Verifies asynchronous webhook payload signatures (legacy signature-only result).
     */
    GatewayVerificationResult verifyWebhook(String rawPayload, Map<String, String> headers);

    /**
     * Verifies and parses structured webhook event details including eventType, orderId, paymentId, amount, and currency.
     */
    default GatewayWebhookResult verifyWebhookEvent(String rawPayload, Map<String, String> headers) {
        GatewayVerificationResult legacy = verifyWebhook(rawPayload, headers);
        return new GatewayWebhookResult(
                legacy.isVerified(),
                legacy.errorMessage(),
                null,
                legacy.providerTransactionId(),
                legacy.verifiedAmount(),
                legacy.currencyCode(),
                legacy.errorCode(),
                legacy.errorMessage()
        );
    }

    /**
     * Queries the upstream provider for the authoritative order/payment status (used during reconciliation).
     */
    default GatewayVerificationResult fetchOrderStatus(String providerOrderId) {
        return new GatewayVerificationResult(false, null, null, null, "UNSUPPORTED_POLL", "Provider does not support status polling");
    }

    /**
     * Executes a full or partial refund against the provider.
     */
    GatewayRefundResult processRefund(RestaurantPayment payment, BigDecimal amount, String reason);

    /**
     * Queries the upstream provider for the authoritative status of an initiated refund.
     */
    default GatewayRefundResult fetchRefundStatus(String providerPaymentId, String providerRefundId) {
        return new GatewayRefundResult(false, providerRefundId, BigDecimal.ZERO, "UNSUPPORTED_REFUND_POLL",
                "Provider does not support refund status polling", true);
    }
}
