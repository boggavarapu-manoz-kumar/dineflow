package com.dineflow.order.dto;

import com.dineflow.order.domain.OrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransitionOrderStatusRequest(
        @NotNull(message = "Target status is required")
        OrderStatus targetStatus,

        @Size(max = 255, message = "Reason cannot exceed 255 characters")
        String reason
) {}
