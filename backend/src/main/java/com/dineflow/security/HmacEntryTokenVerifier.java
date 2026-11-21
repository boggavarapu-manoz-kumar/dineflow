package com.dineflow.security;

import com.dineflow.branch.domain.Branch;
import com.dineflow.branch.repository.BranchRepository;
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
    private final BranchRepository branchRepository;

    public HmacEntryTokenVerifier(
            @Value("${dineflow.security.qr.salt:dineflow-qr-salt-production-value}") String salt,
            @Value("${dineflow.security.qr.max-age-seconds:86400}") long maxAgeSeconds,
            BranchRepository branchRepository) {
        this.salt = salt;
        this.maxAgeSeconds = maxAgeSeconds; // Max 24 hours validity
        this.branchRepository = branchRepository;
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

            // Supports 5-part branch token: tenantId:branchId:tableNumber:timestamp:signature
            // Supports 4-part legacy token: tenantId:tableNumber:timestamp:signature
            if (parts.length == 5) {
                UUID tenantId = UUID.fromString(parts[0]);
                UUID branchId = UUID.fromString(parts[1]);
                String tableNumber = parts[2];
                long timestamp = Long.parseLong(parts[3]);
                String signature = parts[4];

                if (!isTimestampValid(timestamp)) {
                    return Optional.empty();
                }

                String payload = parts[0] + ":" + parts[1] + ":" + parts[2] + ":" + parts[3];
                if (!isSignatureValid(payload, signature)) {
                    return Optional.empty();
                }

                // Verify branch belongs to tenant and is active
                Optional<Branch> branchOpt = branchRepository.findByIdAndTenantId(branchId, tenantId);
                if (branchOpt.isEmpty() || !Boolean.TRUE.equals(branchOpt.get().getIsActive())) {
                    return Optional.empty();
                }

                return Optional.of(new VerifiedEntryContext(tenantId, branchId, tableNumber));

            } else if (parts.length == 4) {
                UUID tenantId = UUID.fromString(parts[0]);
                String tableNumber = parts[1];
                long timestamp = Long.parseLong(parts[2]);
                String signature = parts[3];

                if (!isTimestampValid(timestamp)) {
                    return Optional.empty();
                }

                String payload = parts[0] + ":" + parts[1] + ":" + parts[2];
                if (!isSignatureValid(payload, signature)) {
                    return Optional.empty();
                }

                // Default legacy token to main branch of tenant if exists
                UUID branchId = branchRepository.findByTenantIdAndIsMainTrue(tenantId)
                        .filter(b -> Boolean.TRUE.equals(b.getIsActive()))
                        .map(Branch::getId)
                        .orElse(null);

                return Optional.of(new VerifiedEntryContext(tenantId, branchId, tableNumber));
            }

            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public String createSignedToken(UUID tenantId, UUID branchId, String tableNumber, long epochSeconds) {
        String payload = tenantId.toString() + ":" + branchId.toString() + ":" + tableNumber + ":" + epochSeconds;
        String sig = computeHmac(payload);
        String full = payload + ":" + sig;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(full.getBytes(StandardCharsets.UTF_8));
    }

    public String createSignedToken(UUID tenantId, String tableNumber, long epochSeconds) {
        return createLegacySignedToken(tenantId, tableNumber, epochSeconds);
    }

    public String createLegacySignedToken(UUID tenantId, String tableNumber, long epochSeconds) {
        String payload = tenantId.toString() + ":" + tableNumber + ":" + epochSeconds;
        String sig = computeHmac(payload);
        String full = payload + ":" + sig;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(full.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isTimestampValid(long timestamp) {
        long now = Instant.now().getEpochSecond();
        long tokenAge = now - timestamp;
        return tokenAge >= -300 && tokenAge <= maxAgeSeconds;
    }

    private boolean isSignatureValid(String payload, String signature) {
        String expectedSig = computeHmac(payload);
        return MessageDigest.isEqual(signature.getBytes(StandardCharsets.UTF_8), expectedSig.getBytes(StandardCharsets.UTF_8));
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
