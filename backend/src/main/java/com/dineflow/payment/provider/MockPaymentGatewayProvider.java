package com.dineflow.payment.provider;

import com.dineflow.billing.domain.RestaurantBill;
import com.dineflow.payment.domain.PaymentAttempt;
import com.dineflow.payment.domain.PaymentProviderType;
import com.dineflow.payment.domain.RestaurantPayment;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

/**
 * Mock payment gateway provider for offline integration and deterministic testing.
 * Strictly restricted to "test" and "dev" profiles — NEVER active in production.
 */
@Component
@Profile({"test", "dev"})
public class MockPaymentGatewayProvider implements PaymentGatewayProvider {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public PaymentProviderType getProviderType() {
        return PaymentProviderType.MOCK;
    }

    @Override
    public GatewayOrderResult createPaymentOrder(RestaurantBill bill, RestaurantPayment payment, PaymentAttempt attempt, String callbackUrl) {
        String mockOrderId = "mock_ord_" + UUID.randomUUID().toString().substring(0, 8);
        return new GatewayOrderResult(
                true,
                mockOrderId,
                "mock_secret_xyz123",
                "https://checkout.dineflow.mock/pay?order_id=" + mockOrderId,
                null,
                null,
                "{\"status\":\"created\",\"order_id\":\"" + mockOrderId + "\"}"
        );
    }

    @Override
    public GatewayVerificationResult verifyPayment(String providerOrderId, String providerTransactionId, String signaturePayload) {
        if ("INVALID_SIG".equals(signaturePayload)) {
            return new GatewayVerificationResult(false, null, null, null, "INVALID_SIGNATURE", "Mock signature validation failed");
        }
        return new GatewayVerificationResult(
                true,
                providerTransactionId != null ? providerTransactionId : "mock_txn_" + UUID.randomUUID(),
                null,
                "INR",
                null,
                null
        );
    }

    @Override
    public GatewayVerificationResult verifyWebhook(String rawPayload, Map<String, String> headers) {
        GatewayWebhookResult structured = verifyWebhookEvent(rawPayload, headers);
        return new GatewayVerificationResult(
                structured.isVerified(),
                structured.providerTransactionId(),
                structured.verifiedAmount(),
                structured.currencyCode(),
                structured.errorCode(),
                structured.eventType()
        );
    }

    @Override
    public GatewayWebhookResult verifyWebhookEvent(String rawPayload, Map<String, String> headers) {
        String sig = headers != null ? headers.getOrDefault("x-razorpay-signature", headers.get("X-Razorpay-Signature")) : null;
        if ("INVALID_SIG".equals(sig)) {
            return new GatewayWebhookResult(false, null, null, null, null, null, "INVALID_WEBHOOK_SIGNATURE", "Mock webhook signature invalid");
        }
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            String eventType = root.path("event").asText("payment.captured");
            JsonNode entity = root.path("payload").path("payment").path("entity");
            String paymentId = entity.path("id").asText("mock_wh_txn_001");
            String orderId = entity.path("order_id").asText(null);
            BigDecimal amount = entity.has("amount")
                    ? BigDecimal.valueOf(entity.path("amount").asLong()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                    : null;
            String currency = entity.path("currency").asText("INR");
            String paymentRef = entity.path("notes").path("paymentReference").asText(null);
            return new GatewayWebhookResult(true, eventType, orderId, paymentId, amount, currency, null, null, paymentRef);
        } catch (Exception e) {
            return new GatewayWebhookResult(true, "payment.captured", null, "mock_wh_txn_001", null, "INR", null, null);
        }
    }

    @Override
    public GatewayVerificationResult fetchOrderStatus(String providerOrderId) {
        return new GatewayVerificationResult(false, null, null, "INR", "PAYMENT_PENDING", "Mock order not yet captured");
    }

    @Override
    public GatewayRefundResult processRefund(RestaurantPayment payment, BigDecimal amount, String reason) {
        return new GatewayRefundResult(true, "mock_ref_" + UUID.randomUUID().toString().substring(0, 8), amount, null, null);
    }
}
