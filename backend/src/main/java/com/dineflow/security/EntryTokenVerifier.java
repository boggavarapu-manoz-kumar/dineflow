package com.dineflow.security;

import java.util.Optional;
import java.util.UUID;

/**
 * Interface contract for validating restaurant/table QR entry tokens.
 * Decouples Sprint 2 authentication from future Sprint 3+ QR generator module.
 */
public interface EntryTokenVerifier {

    record VerifiedEntryContext(UUID tenantId, String tableNumber) {}

    Optional<VerifiedEntryContext> verifyEntryToken(String signedEntryToken);
}