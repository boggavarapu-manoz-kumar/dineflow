package com.dineflow.pricing.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PricingBreakdown(
        String currencyCode,
        int totalItemCount,
        BigDecimal itemsSubtotal,
        BigDecimal taxPercentage,
        BigDecimal taxAmount,
        BigDecimal serviceChargePercentage,
        BigDecimal serviceChargeAmount,
        BigDecimal grandTotal,
        List<CalculatedLineItem> lineItems
) {
    public record CalculatedLineItem(
            UUID cartItemId,
            UUID menuItemId,
            String itemName,
            UUID variantId,
            String variantName,
            BigDecimal basePrice,
            BigDecimal variantPrice,
            BigDecimal optionsSubtotal,
            BigDecimal effectiveUnitPrice,
            int quantity,
            BigDecimal lineTotal,
            List<CalculatedOption> selectedOptions
    ) {}

    public record CalculatedOption(
            UUID optionId,
            String optionName,
            BigDecimal additionalPrice
    ) {}
}
