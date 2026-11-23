package com.dineflow.reporting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record FinancialReportResponse(
        UUID tenantId,
        UUID branchId,
        String periodType,
        LocalDate fromDate,
        LocalDate toDate,
        String timezone,
        int businessDayCutoffHour,
        Instant windowStartUtc,
        Instant windowEndUtc,
        String currencyCode,
        // Bill & Sales Metrics
        long billCount,
        BigDecimal grossSales,
        BigDecimal discountTotal,
        BigDecimal netSales,
        BigDecimal serviceChargeTotal,
        BigDecimal taxTotal,
        BigDecimal totalBillValue,
        BigDecimal averageBillValue,
        // Outstanding & Collection Metrics
        long outstandingBillCount,
        BigDecimal outstandingBalance,
        BigDecimal confirmedCollectionTotal,
        BigDecimal cashCollectionTotal,
        BigDecimal onlineCollectionTotal,
        // Refund & Net Collection Metrics
        long refundCount,
        BigDecimal refundTotal,
        BigDecimal netConfirmedCollection,
        // Order Metrics
        long totalOrderCount,
        BigDecimal totalOrderValue,
        long cancelledOrderCount,
        BigDecimal cancelledOrderValue,
        // Breakdowns & Trends
        List<PaymentMethodBreakdown> paymentMethodBreakdown,
        List<BranchSalesSummary> branchBreakdown,
        List<SalesTrendPoint> dailyTrend,
        List<SalesTrendPoint> monthlyTrend,
        Map<String, String> formulaDefinitions
) {
    public record PaymentMethodBreakdown(
            String paymentMethod,
            long transactionCount,
            BigDecimal confirmedAmount
    ) {}

    public record BranchSalesSummary(
            UUID branchId,
            String branchName,
            long billCount,
            BigDecimal grossSales,
            BigDecimal discountTotal,
            BigDecimal netSales,
            BigDecimal totalBillValue,
            BigDecimal confirmedCollection,
            BigDecimal refundTotal,
            BigDecimal netConfirmedCollection,
            BigDecimal outstandingBalance
    ) {}

    public record SalesTrendPoint(
            String bucketLabel,
            long billCount,
            BigDecimal grossSales,
            BigDecimal netSales,
            BigDecimal totalBillValue
    ) {}
}
