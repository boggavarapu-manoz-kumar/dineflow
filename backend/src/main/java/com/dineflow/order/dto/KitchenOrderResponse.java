package com.dineflow.order.dto;

import com.dineflow.order.domain.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record KitchenOrderResponse(
        UUID orderId,
        String orderNumber,
        String tableNumber,
        OrderStatus status,
        UUID branchId,
        int totalItemCount,
        String specialNotes,
        Instant createdAt,
        Instant acceptedAt,
        List<KitchenItemResponse> items
) {
    public record KitchenItemResponse(
            UUID itemId,
            String itemName,
            String variantName,
            int quantity,
            String specialInstructions,
            List<String> options
    ) {}
}
