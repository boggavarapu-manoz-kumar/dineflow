package com.dineflow.pricing.dto;

import java.math.BigDecimal;
import java.util.List;

public record PriceVerificationResult(
        boolean hasPriceChanged,
        boolean hasAvailabilityChanged,
        BigDecimal expectedTotal,
        BigDecimal authoritativeTotal,
        BigDecimal difference,
        List<String> warnings,
        PricingBreakdown authoritativeBreakdown
) {}
