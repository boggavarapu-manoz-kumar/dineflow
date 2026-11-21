package com.dineflow.security;

import java.util.Optional;
import java.util.UUID;

/**
 * Interface contract for validating restaurant/table QR entry tokens.
 * Supports both legacy 4-part and branch-aware 5-part signatures.
 */
public interface EntryTokenVerifier {

    record VerifiedEntryContext(UUID tenantId, UUID branchId, String tableNumber) {}

    Optional<VerifiedEntryContext> verifyEntryToken(String signedEntryToken);
}
