package com.dineflow.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Component
public class HmacEntryTokenVerifier implements EntryTokenVerifier {

    private final String salt;
    private final long maxAgeSeconds;

    public HmacEntryTokenVerifier(
            @Value("${dineflow.security.qr.salt:dineflow-qr-salt-production-value}") String salt,
            @Value("${dineflow.security.qr.max-age-seconds:86400}") long maxAgeSeconds) {
        this.salt = salt;
        this.maxAgeSeconds = maxAgeSeconds; // Max 24 hours validity
    }

    @Override
    public Optional<VerifiedEntryContext> verifyEntryToken(String signedEntryToken) {
        if (signedEntryToken == null || signedEntryToken.isBlank()) {
            return Optional.empty();
        }

        try {
            byte[] decoded = Base64.getUrlDecoder().decode(signedEntryToken.trim());
            String tokenStr = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = tokenStr.split(":");
            if (parts.length != 4) {
                return Optional.empty();
            }

            UUID tenantId = UUID.fromString(parts[0]);
            String tableNumber = parts[1];
            long timestamp = Long.parseLong(parts[2]);
            String signature = parts[3];

            // Expiration enforcement: reject tokens older than maxAgeSeconds or timestamp in distant future
            long now = Instant.now().getEpochSecond();
            long tokenAge = now - timestamp;
            if (tokenAge < -300 || tokenAge > maxAgeSeconds) {
                return Optional.empty();
            }

            // Cryptographic HMAC-SHA256 signature verification
            String payload = parts[0] + ":" + parts[1] + ":" + parts[2];
            String expectedSig = computeHmac(payload);

            if (!MessageDigest.isEqual(signature.getBytes(StandardCharsets.UTF_8), expectedSig.getBytes(StandardCharsets.UTF_8))) {
                return Optional.empty();
            }

            return Optional.of(new VerifiedEntryContext(tenantId, tableNumber));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public String createSignedToken(UUID tenantId, String tableNumber, long epochSeconds) {
        String payload = tenantId.toString() + ":" + tableNumber + ":" + epochSeconds;
        String sig = computeHmac(payload);
        String full = payload + ":" + sig;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(full.getBytes(StandardCharsets.UTF_8));
    }

    private String computeHmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(salt.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC", e);
        }
    }
}