package com.dineflow.config;

import com.dineflow.payment.config.RazorpayProperties;
import com.dineflow.payment.domain.PaymentProviderType;
import com.dineflow.payment.provider.PaymentGatewayProvider;
import com.dineflow.security.JwtProperties;
import com.dineflow.security.ratelimit.RateLimitProperties;
import com.dineflow.storage.StorageProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fail-fast startup validator active strictly in the "prod" profile.
 * Guarantees that insecure development defaults, missing secrets, wildcard CORS,
 * insecure cookies, or mock payment gateways can NEVER boot in production.
 */
@Component
@Profile("prod")
public class ProductionConfigurationValidator {

    private static final Logger log = LoggerFactory.getLogger(ProductionConfigurationValidator.class);

    public static final String DEFAULT_DEV_JWT_SECRET = "dineflow-default-ultra-secure-hmac-sha-256-secret-key-32-bytes!";
    public static final String DEFAULT_DEV_QR_SALT = "dineflow-qr-salt-production-value";

    private final JwtProperties jwtProperties;
    private final RazorpayProperties razorpayProperties;
    private final StorageProperties storageProperties;
    private final RateLimitProperties rateLimitProperties;
    private final List<PaymentGatewayProvider> providers;
    private final String qrSalt;
    private final String corsAllowedOrigins;
    private final boolean cookieSecure;
    private final String realtimeFanout;

    public ProductionConfigurationValidator(
            JwtProperties jwtProperties,
            RazorpayProperties razorpayProperties,
            StorageProperties storageProperties,
            RateLimitProperties rateLimitProperties,
            List<PaymentGatewayProvider> providers,
            @Value("${dineflow.security.qr.salt:}") String qrSalt,
            @Value("${dineflow.cors.allowed-origins:}") String corsAllowedOrigins,
            @Value("${dineflow.security.cookie.secure:true}") boolean cookieSecure,
            @Value("${dineflow.realtime.fanout:local}") String realtimeFanout) {
        this.jwtProperties = jwtProperties;
        this.razorpayProperties = razorpayProperties;
        this.storageProperties = storageProperties;
        this.rateLimitProperties = rateLimitProperties;
        this.providers = providers;
        this.qrSalt = qrSalt;
        this.corsAllowedOrigins = corsAllowedOrigins;
        this.cookieSecure = cookieSecure;
        this.realtimeFanout = realtimeFanout;
    }

    @PostConstruct
    public void validateOnStartup() {
        validate(
                jwtProperties.getSecret(),
                qrSalt,
                razorpayProperties,
                providers,
                corsAllowedOrigins,
                cookieSecure
        );
        validateStorage(storageProperties);
        validateRateLimit(rateLimitProperties);
        validateRealtimeFanout(realtimeFanout);
        log.info("Production configuration validation passed cleanly (secrets, CORS, cookies, storage, Redis rate-limit, Redis realtime fan-out, and payment providers verified).");
    }

    public static void validateRealtimeFanout(String realtimeFanout) {
        if (!"redis".equalsIgnoreCase(realtimeFanout)) {
            throw new IllegalStateException("PROD CONFIG ERROR: dineflow.realtime.fanout must be 'redis' in production for multi-replica WebSocket delivery.");
        }
    }

    public static void validateRateLimit(RateLimitProperties rateLimitProperties) {
        if (rateLimitProperties == null || !rateLimitProperties.isEnabled() || !"redis".equalsIgnoreCase(rateLimitProperties.getStore())) {
            throw new IllegalStateException("PROD CONFIG ERROR: dineflow.rate-limit.enabled must be true and dineflow.rate-limit.store must be 'redis' in production.");
        }
    }

    public static void validateStorage(StorageProperties storageProperties) {
        if (storageProperties == null || !"s3".equalsIgnoreCase(storageProperties.getType())) {
            throw new IllegalStateException("PROD CONFIG ERROR: dineflow.storage.type must be 's3' (cloud object storage) in production.");
        }
        StorageProperties.S3Config s3 = storageProperties.getS3();
        if (s3 == null || isPlaceholderOrBlank(s3.getBucket()) || isPlaceholderOrBlank(s3.getRegion())) {
            throw new IllegalStateException("PROD CONFIG ERROR: dineflow.storage.s3.bucket and dineflow.storage.s3.region must be configured in production.");
        }
    }

    public static void validate(String jwtSecret,
                                String qrSalt,
                                RazorpayProperties razorpay,
                                List<PaymentGatewayProvider> providers,
                                String corsAllowedOrigins,
                                boolean cookieSecure) {
        if (jwtSecret == null || jwtSecret.isBlank() || jwtSecret.length() < 32 || DEFAULT_DEV_JWT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException("PROD CONFIG ERROR: dineflow.security.jwt.secret must be a non-default secret of at least 32 characters.");
        }
        if (qrSalt == null || qrSalt.isBlank() || qrSalt.length() < 16 || DEFAULT_DEV_QR_SALT.equals(qrSalt)) {
            throw new IllegalStateException("PROD CONFIG ERROR: dineflow.security.qr.salt must be a non-default secret of at least 16 characters.");
        }
        if (!cookieSecure) {
            throw new IllegalStateException("PROD CONFIG ERROR: dineflow.security.cookie.secure must be true in production.");
        }
        if (corsAllowedOrigins == null || corsAllowedOrigins.isBlank() || corsAllowedOrigins.contains("*")) {
            throw new IllegalStateException("PROD CONFIG ERROR: dineflow.cors.allowed-origins must specify explicit origins without wildcards (*).");
        }
        if (providers != null && providers.stream().anyMatch(p -> p.getProviderType() == PaymentProviderType.MOCK)) {
            throw new IllegalStateException("PROD CONFIG ERROR: MockPaymentGatewayProvider must NEVER be active in production.");
        }
        if (razorpay != null && razorpay.isEnabled()) {
            if (isPlaceholderOrBlank(razorpay.getKeyId())
                    || isPlaceholderOrBlank(razorpay.getKeySecret())
                    || isPlaceholderOrBlank(razorpay.getWebhookSecret())) {
                throw new IllegalStateException("PROD CONFIG ERROR: Razorpay keyId, keySecret, and webhookSecret must be real non-placeholder values in production.");
            }
        }
    }

    private static boolean isPlaceholderOrBlank(String val) {
        return val == null || val.isBlank() || val.toLowerCase().contains("placeholder");
    }
}
