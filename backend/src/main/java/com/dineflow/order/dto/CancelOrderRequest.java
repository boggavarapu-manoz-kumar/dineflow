package com.dineflow.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelOrderRequest(
        @NotBlank(message = "Cancellation reason is mandatory")
        @Size(max = 255, message = "Reason cannot exceed 255 characters")
        String reason
) {}
