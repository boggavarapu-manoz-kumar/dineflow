package com.dineflow.identity.dto;

import java.time.Instant;
import java.util.UUID;

public record CustomerSessionResponse(
        String guestToken,
        UUID sessionId,
        UUID tenantId,
        String tableNumber,
        String customerName,
        Instant expiresAt
) {}