package com.dineflow.order.dto;

import java.time.Instant;
import java.util.UUID;

public record OrderStatusHistoryResponse(
        UUID id,
        UUID orderId,
        String previousStatus,
        String newStatus,
        String actorType,
        UUID changedByUserId,
        String changedByRole,
        String reason,
        Instant createdAt
) {}
