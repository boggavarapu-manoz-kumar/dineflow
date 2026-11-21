package com.dineflow.cart.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CartResponse(
        UUID cartId,
        UUID tenantId,
        UUID branchId,
        UUID customerSessionId,
        String tableNumber,
        String currencyCode,
        int totalItemCount,
        BigDecimal subtotal,
        BigDecimal taxPercentage,
        BigDecimal taxAmount,
        BigDecimal serviceChargePercentage,
        BigDecimal serviceChargeAmount,
        BigDecimal grandTotal,
        List<CartItemResponse> items
) {
    public record CartItemResponse(
            UUID cartItemId,
            UUID menuItemId,
            String itemName,
            UUID variantId,
            String variantName,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal,
            String specialInstructions,
            boolean isAvailable,
            List<CartOptionResponse> selectedOptions
    ) {}

    public record CartOptionResponse(
            UUID optionId,
            String optionName,
            BigDecimal additionalPrice
    ) {}
}
