package com.dineflow.pricing.dto;

import java.math.BigDecimal;

public record ValidateCartPricingRequest(
        BigDecimal expectedGrandTotal
) {}
