package com.dineflow.order.dto;

import com.dineflow.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        String orderNumber,
        OrderStatus status,
        UUID tenantId,
        UUID branchId,
        String tableNumber,
        String currencyCode,
        int totalItemCount,
        BigDecimal subtotal,
        BigDecimal taxPercentage,
        BigDecimal taxAmount,
        BigDecimal serviceChargePercentage,
        BigDecimal serviceChargeAmount,
        BigDecimal grandTotal,
        String specialNotes,
        UUID assignedWaiterId,
        Instant createdAt,
        List<OrderItemResponse> items
) {
    public record OrderItemResponse(
            UUID itemId,
            UUID menuItemId,
            String itemName,
            UUID variantId,
            String variantName,
            int quantity,
            BigDecimal baseUnitPrice,
            BigDecimal optionsUnitPrice,
            BigDecimal effectiveUnitPrice,
            BigDecimal lineTotal,
            String specialInstructions,
            List<OrderItemOptionResponse> options
    ) {}

    public record OrderItemOptionResponse(
            UUID optionId,
            String optionName,
            BigDecimal priceContribution
    ) {}
}
