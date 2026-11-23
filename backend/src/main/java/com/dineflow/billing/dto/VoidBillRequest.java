package com.dineflow.billing.dto;

import jakarta.validation.constraints.NotBlank;

public record VoidBillRequest(
        @NotBlank(message = "Void reason is mandatory")
        String reason
) {}
