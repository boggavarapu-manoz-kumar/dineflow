package com.dineflow.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AssignWaiterRequest(
        @NotNull(message = "Waiter ID is required")
        UUID waiterId,

        @Size(max = 255, message = "Reason cannot exceed 255 characters")
        String reason
) {}
