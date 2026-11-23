package com.dineflow.billing.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record GenerateBillRequest(
        List<UUID> orderIds,
        BigDecimal discountAmount,
        String specialNote
) {}
