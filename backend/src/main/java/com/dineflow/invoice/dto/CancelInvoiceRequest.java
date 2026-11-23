package com.dineflow.invoice.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelInvoiceRequest(
        @NotBlank(message = "Cancellation reason is mandatory")
        String reason
) {}
