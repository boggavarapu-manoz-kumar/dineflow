package com.dineflow.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BillResponse(
        UUID id,
        UUID tenantId,
        UUID branchId,
        UUID customerSessionId,
        String billNumber,
        String tableNumber,
        String status,
        String currencyCode,
        String restaurantName,
        String branchName,
        String branchAddress,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxPercentage,
        BigDecimal taxAmount,
        BigDecimal serviceChargePercentage,
        BigDecimal serviceChargeAmount,
        BigDecimal grandTotal,
        BigDecimal paidAmount,
        BigDecimal outstandingBalance,
        String voidReason,
        Instant issuedAt,
        Instant paidAt,
        Instant voidedAt,
        List<String> orderNumbers,
        List<BillItemResponse> items,
        List<BillStatusHistoryResponse> statusHistory
) {
    public record BillItemResponse(
            UUID id,
            UUID orderId,
            String itemName,
            String variantName,
            String itemDescription,
            int quantity,
            BigDecimal baseUnitPrice,
            BigDecimal optionsUnitPrice,
            BigDecimal effectiveUnitPrice,
            BigDecimal lineTotal,
            String specialInstructions,
            List<BillOptionResponse> options
    ) {}

    public record BillOptionResponse(
            UUID id,
            String optionName,
            BigDecimal priceContribution
    ) {}

    public record BillStatusHistoryResponse(
            UUID id,
            String previousStatus,
            String newStatus,
            UUID changedByUserId,
            String changedByRole,
            String actorType,
            String reason,
            Instant createdAt
    ) {}
}
