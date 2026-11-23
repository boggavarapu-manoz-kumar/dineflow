package com.dineflow.payment.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record ReversePaymentRequest(
        @NotBlank(message = "Reversal reason is mandatory")
        String reason
) {}
