package com.dineflow.payment.provider;

import com.dineflow.billing.domain.RestaurantBill;
import com.dineflow.payment.config.RazorpayProperties;
import com.dineflow.payment.domain.PaymentAttempt;
import com.dineflow.payment.domain.PaymentProviderType;
import com.dineflow.payment.domain.RestaurantPayment;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

/**
 * Production-ready Razorpay implementation of {@link PaymentGatewayProvider}.
 * Interacts with Razorpay Orders, Payments, and Refunds REST APIs via Java HttpClient
 * and constant-time HMAC-SHA256 signature verification.
 * Distinguishes definite 4xx rejections from ambiguous timeouts/5xx/malformed responses
 * so callers never misclassify uncertain network outcomes as confirmed failures.
 */
@Component
public class RazorpayPaymentGatewayProvider implements PaymentGatewayProvider {

    private static final Logger log = LoggerFactory.getLogger(RazorpayPaymentGatewayProvider.class);
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final RazorpayProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public RazorpayPaymentGatewayProvider(RazorpayProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(500L, properties.getConnectTimeoutMs())))
                .build();
    }

    @Override
    public PaymentProviderType getProviderType() {
        return PaymentProviderType.RAZORPAY;
    }

    @Override
    public GatewayOrderResult createPaymentOrder(RestaurantBill bill, RestaurantPayment payment, PaymentAttempt attempt, String callbackUrl) {
        if (!properties.isEnabled()) {
            return new GatewayOrderResult(false, null, null, null, "RAZORPAY_DISABLED", "Razorpay is not enabled", null, false);
        }

        try {
            long amountInSubunits = payment.getPayableAmount()
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValue();

            String receiptId = payment.getPaymentReference();
            if (receiptId.length() > 40) {
                receiptId = receiptId.substring(0, 40);
            }

            Map<String, Object> requestPayload = Map.of(
                    "amount", amountInSubunits,
                    "currency", payment.getCurrencyCode(),
                    "receipt", receiptId,
                    "notes", Map.of(
                            "tenantId", payment.getTenantId().toString(),
                            "billId", bill.getId().toString(),
                            "paymentReference", payment.getPaymentReference(),
                            "idempotencyKey", attempt.getIdempotencyKey()
                    )
            );

            String requestBody = objectMapper.writeValueAsString(requestPayload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getBaseUrl() + "/orders"))
                    .timeout(Duration.ofMillis(Math.max(500L, properties.getReadTimeoutMs())))
                    .header("Authorization", buildBasicAuthHeader())
                    .header("Content-Type", "application/json")
                    .header("X-Idempotency-Key", attempt.getIdempotencyKey())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                try {
                    JsonNode root = objectMapper.readTree(response.body());
                    String providerOrderId = root.path("id").asText(null);
                    if (providerOrderId == null || providerOrderId.isBlank()) {
                        log.error("Razorpay returned 2xx without order id for payment {}", payment.getPaymentReference());
                        return new GatewayOrderResult(
                                false, null, null, null,
                                "MALFORMED_PROVIDER_RESPONSE",
                                "Provider returned 2xx response without an order ID; reconciliation required",
                                response.body(),
                                true
                        );
                    }
                    return new GatewayOrderResult(
                            true,
                            providerOrderId,
                            properties.getKeyId(),
                            null,
                            null,
                            null,
                            response.body(),
                            false
                    );
                } catch (Exception parseEx) {
                    log.error("Malformed JSON in Razorpay 2xx response for payment {}: {}", payment.getPaymentReference(), parseEx.getMessage());
                    return new GatewayOrderResult(
                            false, null, null, null,
                            "MALFORMED_PROVIDER_RESPONSE",
                            "Malformed provider response payload; reconciliation required",
                            response.body(),
                            true
                    );
                }
            } else if (response.statusCode() >= 500 || response.statusCode() == 408 || response.statusCode() == 429) {
                log.warn("Razorpay transient/ambiguous HTTP {} for payment {}", response.statusCode(), payment.getPaymentReference());
                return new GatewayOrderResult(
                        false,
                        null,
                        null,
                        null,
                        "GATEWAY_TRANSIENT_HTTP_" + response.statusCode(),
                        "Provider returned transient HTTP " + response.statusCode() + "; outcome ambiguous",
                        response.body(),
                        true
                );
            } else {
                log.error("Razorpay order creation rejected with HTTP {}", response.statusCode());
                return new GatewayOrderResult(
                        false,
                        null,
                        null,
                        null,
                        "GATEWAY_ORDER_FAILED_" + response.statusCode(),
                        "Failed to create order with Razorpay (HTTP " + response.statusCode() + ")",
                        response.body(),
                        false
                );
            }
        } catch (HttpConnectTimeoutException e) {
            log.warn("Connect timeout contacting Razorpay for payment {}: {}", payment.getPaymentReference(), e.getMessage());
            return new GatewayOrderResult(
                    false, null, null, null,
                    "GATEWAY_CONNECT_TIMEOUT",
                    "Connection timeout contacting Razorpay: " + e.getMessage(),
                    null,
                    true
            );
        } catch (HttpTimeoutException e) {
            log.warn("Read timeout contacting Razorpay for payment {}: {}", payment.getPaymentReference(), e.getMessage());
            return new GatewayOrderResult(
                    false, null, null, null,
                    "GATEWAY_READ_TIMEOUT",
                    "Response timeout waiting for Razorpay; provider may have created order",
                    null,
                    true
            );
        } catch (Exception e) {
            log.error("Network/IO exception connecting to Razorpay orders API: {}", e.getMessage());
            return new GatewayOrderResult(
                    false,
                    null,
                    null,
                    null,
                    "GATEWAY_IO_EXCEPTION",
                    "Network error communicating with Razorpay: " + e.getMessage(),
                    null,
                    true
            );
        }
    }

    /**
     * Verifies the client checkout response via Razorpay standard HMAC-SHA256:
     * signature = HMAC_SHA256(order_id + "|" + payment_id, secret)
     */
    @Override
    public GatewayVerificationResult verifyPayment(String providerOrderId, String providerTransactionId, String signaturePayload) {
        if (providerOrderId == null || providerTransactionId == null || signaturePayload == null) {
            return new GatewayVerificationResult(false, null, null, null, "MISSING_VERIFICATION_DATA", "Order ID, payment ID, and signature are required");
        }

        try {
            String payload = providerOrderId + "|" + providerTransactionId;
            String expectedSignature = calculateHmacSha256(payload, properties.getKeySecret());

            if (constantTimeEquals(expectedSignature, signaturePayload)) {
                return new GatewayVerificationResult(true, providerTransactionId, null, null, null, null);
            } else {
                log.warn("Razorpay payment signature mismatch for orderId: {}, txnId: {}", providerOrderId, providerTransactionId);
                return new GatewayVerificationResult(false, providerTransactionId, null, null, "SIGNATURE_MISMATCH", "Razorpay signature mismatch");
            }
        } catch (Exception e) {
            log.error("Error verifying Razorpay payment signature: {}", e.getMessage());
            return new GatewayVerificationResult(false, providerTransactionId, null, null, "VERIFICATION_ERROR", e.getMessage());
        }
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
                structured.eventType() != null ? structured.eventType() : structured.errorMessage()
        );
    }

    /**
     * Verifies incoming Razorpay webhook using exact raw body & X-Razorpay-Signature header,
     * extracting eventType, order_id, payment_id, amount, and currency.
     */
    @Override
    public GatewayWebhookResult verifyWebhookEvent(String rawPayload, Map<String, String> headers) {
        String webhookSignature = headers != null
                ? headers.getOrDefault("x-razorpay-signature", headers.get("X-Razorpay-Signature"))
                : null;

        if (webhookSignature == null || webhookSignature.isBlank()) {
            return new GatewayWebhookResult(false, null, null, null, null, null, "MISSING_WEBHOOK_SIGNATURE", "X-Razorpay-Signature header missing");
        }

        try {
            String expectedSignature = calculateHmacSha256(rawPayload, properties.getWebhookSecret());
            if (!constantTimeEquals(expectedSignature, webhookSignature)) {
                return new GatewayWebhookResult(false, null, null, null, null, null, "INVALID_WEBHOOK_SIGNATURE", "Webhook signature verification failed");
            }

            JsonNode root = objectMapper.readTree(rawPayload);
            String eventType = root.path("event").asText();
            JsonNode payloadEntity = root.path("payload").path("payment").path("entity");
            String paymentId = payloadEntity.path("id").asText(null);
            String orderId = payloadEntity.path("order_id").asText(null);
            BigDecimal amount = payloadEntity.has("amount")
                    ? BigDecimal.valueOf(payloadEntity.path("amount").asLong()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                    : null;
            String currency = payloadEntity.path("currency").asText(null);
            String paymentRef = payloadEntity.path("notes").path("paymentReference").asText(null);
            if (paymentRef == null || paymentRef.isBlank()) {
                paymentRef = root.path("payload").path("order").path("entity").path("receipt").asText(null);
            }

            return new GatewayWebhookResult(true, eventType, orderId, paymentId, amount, currency, null, null, paymentRef);
        } catch (Exception e) {
            log.error("Error processing Razorpay webhook: {}", e.getMessage());
            return new GatewayWebhookResult(false, null, null, null, null, null, "WEBHOOK_PARSE_ERROR", e.getMessage());
        }
    }

    /**
     * Queries Razorpay Orders Payments API (GET /v1/orders/{orderId}/payments) to determine
     * authoritative status during reconciliation of stale/ambiguous attempts.
     */
    @Override
    public GatewayVerificationResult fetchOrderStatus(String providerOrderId) {
        if (!properties.isEnabled() || providerOrderId == null || providerOrderId.isBlank()) {
            return new GatewayVerificationResult(false, null, null, null, "INVALID_ORDER_ID", "Cannot poll Razorpay without order ID", true);
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getBaseUrl() + "/orders/" + providerOrderId + "/payments"))
                    .timeout(Duration.ofMillis(Math.max(500L, properties.getReadTimeoutMs())))
                    .header("Authorization", buildBasicAuthHeader())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode items = objectMapper.readTree(response.body()).path("items");
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        if ("captured".equalsIgnoreCase(item.path("status").asText())) {
                            String payId = item.path("id").asText();
                            BigDecimal amt = BigDecimal.valueOf(item.path("amount").asLong())
                                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                            String curr = item.path("currency").asText();
                            return new GatewayVerificationResult(true, payId, amt, curr, null, null, false);
                        }
                    }
                }
                return new GatewayVerificationResult(false, null, null, null, "NOT_CAPTURED", "No captured payment found for order", false);
            }
            boolean isTransient = response.statusCode() >= 500 || response.statusCode() == 408 || response.statusCode() == 429;
            return new GatewayVerificationResult(false, null, null, null, "POLL_HTTP_" + response.statusCode(), "Razorpay status poll failed", isTransient);
        } catch (Exception e) {
            log.error("Error polling Razorpay order {}: {}", providerOrderId, e.getMessage());
            return new GatewayVerificationResult(false, null, null, null, "GATEWAY_IO_EXCEPTION", e.getMessage(), true);
        }
    }

    /**
     * Executes a real refund via Razorpay Refunds API: POST /v1/payments/{paymentId}/refund.
     */
    @Override
    public GatewayRefundResult processRefund(RestaurantPayment payment, BigDecimal amount, String reason) {
        if (!properties.isEnabled()) {
            return new GatewayRefundResult(false, null, BigDecimal.ZERO, "RAZORPAY_DISABLED", "Razorpay is not enabled", false);
        }
        if (payment.getProviderPaymentId() == null || payment.getProviderPaymentId().isBlank()) {
            return new GatewayRefundResult(false, null, BigDecimal.ZERO, "MISSING_PROVIDER_PAYMENT_ID", "Original Razorpay payment ID is missing", false);
        }

        try {
            long amountInSubunits = amount
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValue();

            Map<String, Object> requestPayload = Map.of(
                    "amount", amountInSubunits,
                    "speed", "normal",
                    "notes", Map.of(
                            "paymentReference", payment.getPaymentReference(),
                            "reason", reason != null ? reason : "Customer refund"
                    )
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getBaseUrl() + "/payments/" + payment.getProviderPaymentId() + "/refund"))
                    .timeout(Duration.ofMillis(Math.max(500L, properties.getReadTimeoutMs())))
                    .header("Authorization", buildBasicAuthHeader())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestPayload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode root = objectMapper.readTree(response.body());
                String refundId = root.path("id").asText();
                return new GatewayRefundResult(true, refundId, amount, null, null, false);
            } else {
                boolean isTransient = response.statusCode() >= 500 || response.statusCode() == 408 || response.statusCode() == 429;
                log.error("Razorpay refund failed with HTTP {}", response.statusCode());
                return new GatewayRefundResult(false, null, BigDecimal.ZERO,
                        "GATEWAY_REFUND_FAILED_" + response.statusCode(),
                        "Razorpay rejected refund request (HTTP " + response.statusCode() + ")",
                        isTransient);
            }
        } catch (Exception e) {
            log.error("Network exception executing Razorpay refund: {}", e.getMessage());
            return new GatewayRefundResult(false, null, BigDecimal.ZERO, "GATEWAY_REFUND_IO_ERROR", e.getMessage(), true);
        }
    }

    @Override
    public GatewayRefundResult fetchRefundStatus(String providerPaymentId, String providerRefundId) {
        if (!properties.isEnabled() || providerPaymentId == null || providerPaymentId.isBlank()
                || providerRefundId == null || providerRefundId.isBlank()) {
            return new GatewayRefundResult(false, providerRefundId, BigDecimal.ZERO, "INVALID_REFUND_POLL_PARAMS",
                    "Missing Razorpay paymentId or refundId", true);
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getBaseUrl() + "/payments/" + providerPaymentId + "/refunds/" + providerRefundId))
                    .timeout(Duration.ofMillis(Math.max(500L, properties.getReadTimeoutMs())))
                    .header("Authorization", buildBasicAuthHeader())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode root = objectMapper.readTree(response.body());
                String status = root.path("status").asText("");
                BigDecimal amount = root.has("amount")
                        ? BigDecimal.valueOf(root.path("amount").asLong()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                if ("processed".equalsIgnoreCase(status)) {
                    return new GatewayRefundResult(true, providerRefundId, amount, null, null, false);
                } else if ("failed".equalsIgnoreCase(status)) {
                    return new GatewayRefundResult(false, providerRefundId, BigDecimal.ZERO, "REFUND_FAILED_AT_PROVIDER",
                            "Provider reported refund status=failed", false);
                }
                return new GatewayRefundResult(false, providerRefundId, BigDecimal.ZERO, "REFUND_STILL_PENDING",
                        "Provider refund status is " + status, true);
            }
            boolean transientErr = response.statusCode() >= 500 || response.statusCode() == 408 || response.statusCode() == 429;
            return new GatewayRefundResult(false, providerRefundId, BigDecimal.ZERO,
                    "REFUND_POLL_HTTP_" + response.statusCode(), "Razorpay refund poll HTTP " + response.statusCode(), transientErr);
        } catch (Exception e) {
            log.error("Error polling Razorpay refund {}: {}", providerRefundId, e.getMessage());
            return new GatewayRefundResult(false, providerRefundId, BigDecimal.ZERO, "REFUND_POLL_IO_ERROR", e.getMessage(), true);
        }
    }

    private String buildBasicAuthHeader() {
        return "Basic " + Base64.getEncoder().encodeToString(
                (properties.getKeyId() + ":" + properties.getKeySecret()).getBytes(StandardCharsets.UTF_8)
        );
    }

    public static String calculateHmacSha256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
        mac.init(secretKey);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
