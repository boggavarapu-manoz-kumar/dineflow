package com.dineflow.order.dto;

import java.time.Instant;
import java.util.UUID;

public record OrderAssignmentResponse(
        UUID id,
        UUID orderId,
        String orderNumber,
        UUID waiterId,
        String waiterName,
        UUID assignedByUserId,
        String assignmentStatus,
        String reason,
        Instant createdAt,
        Instant unassignedAt
) {}
