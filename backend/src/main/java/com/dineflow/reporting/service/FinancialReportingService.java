package com.dineflow.reporting.service;

import com.dineflow.billing.domain.RestaurantBill;
import com.dineflow.billing.repository.RestaurantBillRepository;
import com.dineflow.branch.domain.Branch;
import com.dineflow.branch.repository.BranchRepository;
import com.dineflow.common.exception.BusinessException;
import com.dineflow.order.repository.RestaurantOrderRepository;
import com.dineflow.payment.domain.PaymentMethod;
import com.dineflow.payment.domain.RestaurantPayment;
import com.dineflow.payment.repository.RestaurantPaymentRepository;
import com.dineflow.payment.repository.RestaurantRefundRepository;
import com.dineflow.reporting.dto.FinancialReportResponse;
import com.dineflow.reporting.dto.ReportPeriodType;
import com.dineflow.security.DineFlowPrincipal;
import com.dineflow.tenant.domain.HotelProfile;
import com.dineflow.tenant.domain.Tenant;
import com.dineflow.tenant.repository.HotelProfileRepository;
import com.dineflow.tenant.repository.TenantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FinancialReportingService {

    private static final int EXPORT_PAGE_SIZE = 500;
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final RestaurantBillRepository billRepository;
    private final RestaurantPaymentRepository paymentRepository;
    private final RestaurantRefundRepository refundRepository;
    private final RestaurantOrderRepository orderRepository;
    private final BranchRepository branchRepository;
    private final TenantRepository tenantRepository;
    private final HotelProfileRepository hotelProfileRepository;
    private final BusinessDateRangeResolver dateRangeResolver;
    private final FinancialReportPdfGenerator pdfGenerator;

    public FinancialReportingService(RestaurantBillRepository billRepository,
                                     RestaurantPaymentRepository paymentRepository,
                                     RestaurantRefundRepository refundRepository,
                                     RestaurantOrderRepository orderRepository,
                                     BranchRepository branchRepository,
                                     TenantRepository tenantRepository,
                                     HotelProfileRepository hotelProfileRepository,
                                     BusinessDateRangeResolver dateRangeResolver,
                                     FinancialReportPdfGenerator pdfGenerator) {
        this.billRepository = billRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.orderRepository = orderRepository;
        this.branchRepository = branchRepository;
        this.tenantRepository = tenantRepository;
        this.hotelProfileRepository = hotelProfileRepository;
        this.dateRangeResolver = dateRangeResolver;
        this.pdfGenerator = pdfGenerator;
    }

    /**
     * Generates an authoritative, database-aggregated financial summary report.
     * Strictly enforces tenant and branch authorization.
     */
    @Transactional(readOnly = true)
    public FinancialReportResponse generateFinancialReport(DineFlowPrincipal principal,
                                                           ReportPeriodType periodType,
                                                           LocalDate fromDate,
                                                           LocalDate toDate,
                                                           UUID requestedBranchId,
                                                           String timezone,
                                                           Integer businessDayCutoffHour) {
        UUID tenantId = principal.getTenantId();
        UUID effectiveBranchId = resolveAuthorizedBranch(principal, requestedBranchId);

        BusinessDateRangeResolver.ResolvedDateRange range = dateRangeResolver.resolve(
                periodType, fromDate, toDate, timezone, businessDayCutoffHour);

        String currencyCode = hotelProfileRepository.findByTenantId(tenantId)
                .map(HotelProfile::getCurrencyCode)
                .filter(c -> c != null && !c.isBlank())
                .orElse("INR");

        // 1. Bill Summary Aggregation (DB-level)
        Object[] billRow = unwrapSingleRow(billRepository.aggregateBillSummary(
                tenantId, effectiveBranchId, range.startUtc(), range.endUtc()));
        long billCount = toLong(billRow[0]);
        BigDecimal grossSales = toMoney(billRow[1]);
        BigDecimal discountTotal = toMoney(billRow[2]);
        BigDecimal serviceChargeTotal = toMoney(billRow[3]);
        BigDecimal taxTotal = toMoney(billRow[4]);
        BigDecimal totalBillValue = toMoney(billRow[5]);
        BigDecimal outstandingBalance = toMoney(billRow[6]);
        long outstandingBillCount = toLong(billRow[7]);

        BigDecimal netSales = grossSales.subtract(discountTotal).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal averageBillValue = billCount > 0
                ? totalBillValue.divide(BigDecimal.valueOf(billCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        // 2. Confirmed Payments Aggregation (strictly excludes PENDING, INITIATED, FAILED, CANCELLED)
        List<Object[]> pmRows = paymentRepository.aggregateConfirmedPaymentsByMethod(
                tenantId, effectiveBranchId, range.startUtc(), range.endUtc());
        List<FinancialReportResponse.PaymentMethodBreakdown> pmBreakdown = new ArrayList<>();
        BigDecimal confirmedCollectionTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal cashCollectionTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal onlineCollectionTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (Object[] row : pmRows) {
            PaymentMethod method = (PaymentMethod) row[0];
            long count = toLong(row[1]);
            BigDecimal amount = toMoney(row[2]);
            pmBreakdown.add(new FinancialReportResponse.PaymentMethodBreakdown(method.name(), count, amount));
            confirmedCollectionTotal = confirmedCollectionTotal.add(amount);
            if (method == PaymentMethod.CASH || method == PaymentMethod.PAY_AT_COUNTER) {
                cashCollectionTotal = cashCollectionTotal.add(amount);
            } else if (method == PaymentMethod.ONLINE_PAYMENT) {
                onlineCollectionTotal = onlineCollectionTotal.add(amount);
            }
        }
        pmBreakdown.sort(Comparator.comparing(FinancialReportResponse.PaymentMethodBreakdown::paymentMethod));

        // 3. Completed Refunds Aggregation (DB-level)
        Object[] refundRow = unwrapSingleRow(refundRepository.aggregateSucceededRefunds(
                tenantId, effectiveBranchId, range.startUtc(), range.endUtc()));
        long refundCount = toLong(refundRow[0]);
        BigDecimal refundTotal = toMoney(refundRow[1]);
        BigDecimal netConfirmedCollection = confirmedCollectionTotal.subtract(refundTotal).setScale(2, RoundingMode.HALF_UP);

        // 4. Order & Cancellation Aggregation (DB-level)
        Object[] orderRow = unwrapSingleRow(orderRepository.aggregateOrderStats(
                tenantId, effectiveBranchId, range.startUtc(), range.endUtc()));
        long totalOrderCount = toLong(orderRow[0]);
        BigDecimal totalOrderValue = toMoney(orderRow[1]);
        long cancelledOrderCount = toLong(orderRow[2]);
        BigDecimal cancelledOrderValue = toMoney(orderRow[3]);

        // 5. Branch-Wise Breakdown
        List<FinancialReportResponse.BranchSalesSummary> branchBreakdown = buildBranchBreakdown(
                tenantId, effectiveBranchId, range.startUtc(), range.endUtc());

        // 6. Daily & Monthly Trends in Configured Timezone & Business Day Cutoff
        List<Object[]> trendRows = billRepository.findBillTimestampsAndTotalsForTrend(
                tenantId, effectiveBranchId, range.startUtc(), range.endUtc());
        Map<String, TrendAcc> dailyMap = new LinkedHashMap<>();
        Map<String, TrendAcc> monthlyMap = new LinkedHashMap<>();

        for (Object[] tr : trendRows) {
            Instant issuedAt = (Instant) tr[0];
            BigDecimal sub = toMoney(tr[1]);
            BigDecimal disc = toMoney(tr[2]);
            BigDecimal gt = toMoney(tr[3]);
            LocalDate bDate = dateRangeResolver.toBusinessDate(issuedAt, range.zoneId(), range.businessDayCutoffHour());
            String dayKey = bDate.toString();
            String monthKey = MONTH_FMT.format(bDate);

            dailyMap.computeIfAbsent(dayKey, k -> new TrendAcc()).add(sub, disc, gt);
            monthlyMap.computeIfAbsent(monthKey, k -> new TrendAcc()).add(sub, disc, gt);
        }

        List<FinancialReportResponse.SalesTrendPoint> dailyTrend = dailyMap.entrySet().stream()
                .map(e -> e.getValue().toPoint(e.getKey()))
                .toList();
        List<FinancialReportResponse.SalesTrendPoint> monthlyTrend = monthlyMap.entrySet().stream()
                .map(e -> e.getValue().toPoint(e.getKey()))
                .toList();

        return new FinancialReportResponse(
                tenantId,
                effectiveBranchId,
                range.periodType().name(),
                range.fromDate(),
                range.toDate(),
                range.zoneId().getId(),
                range.businessDayCutoffHour(),
                range.startUtc(),
                range.endUtc(),
                currencyCode,
                billCount,
                grossSales,
                discountTotal,
                netSales,
                serviceChargeTotal,
                taxTotal,
                totalBillValue,
                averageBillValue,
                outstandingBillCount,
                outstandingBalance,
                confirmedCollectionTotal.setScale(2, RoundingMode.HALF_UP),
                cashCollectionTotal.setScale(2, RoundingMode.HALF_UP),
                onlineCollectionTotal.setScale(2, RoundingMode.HALF_UP),
                refundCount,
                refundTotal,
                netConfirmedCollection,
                totalOrderCount,
                totalOrderValue,
                cancelledOrderCount,
                cancelledOrderValue,
                pmBreakdown,
                branchBreakdown,
                dailyTrend,
                monthlyTrend,
                buildFormulaDefinitions()
        );
    }

    /**
     * Memory-safe paginated streaming CSV export.
     * Streams bills or confirmed payments in chunks of 500 rows directly to OutputStream.
     */
    @Transactional(readOnly = true)
    public void streamCsvExport(DineFlowPrincipal principal,
                                ReportPeriodType periodType,
                                LocalDate fromDate,
                                LocalDate toDate,
                                UUID requestedBranchId,
                                PaymentMethod paymentMethodFilter,
                                String timezone,
                                Integer businessDayCutoffHour,
                                OutputStream outputStream) throws IOException {
        UUID tenantId = principal.getTenantId();
        UUID effectiveBranchId = resolveAuthorizedBranch(principal, requestedBranchId);
        BusinessDateRangeResolver.ResolvedDateRange range = dateRangeResolver.resolve(
                periodType, fromDate, toDate, timezone, businessDayCutoffHour);

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
            if (paymentMethodFilter != null) {
                writer.write("PaymentRef,BillNumber,Method,Status,Provider,AmountPaid,RefundedAmount,Currency,SettledAtUTC\n");
                int pageNum = 0;
                Page<RestaurantPayment> page;
                do {
                    page = paymentRepository.findConfirmedPageForExport(
                            tenantId, effectiveBranchId, paymentMethodFilter, range.startUtc(), range.endUtc(),
                            PageRequest.of(pageNum++, EXPORT_PAGE_SIZE));
                    for (RestaurantPayment p : page.getContent()) {
                        writer.write(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                                csvEscape(p.getPaymentReference()),
                                csvEscape(p.getBill() != null ? p.getBill().getBillNumber() : ""),
                                p.getPaymentMethod().name(),
                                p.getPaymentStatus().name(),
                                p.getPaymentProvider().name(),
                                p.getPaidAmount().toPlainString(),
                                p.getRefundedAmount().toPlainString(),
                                p.getCurrencyCode(),
                                p.getSettledAt() != null ? p.getSettledAt().toString() : ""));
                    }
                    writer.flush();
                } while (page.hasNext());
            } else {
                writer.write("BillNumber,Table,Status,IssuedAtUTC,Subtotal,Discount,ServiceCharge,Tax,GrandTotal,PaidAmount,OutstandingBalance,Currency\n");
                int pageNum = 0;
                Page<RestaurantBill> page;
                do {
                    page = billRepository.findPageForExport(
                            tenantId, effectiveBranchId, range.startUtc(), range.endUtc(),
                            PageRequest.of(pageNum++, EXPORT_PAGE_SIZE));
                    for (RestaurantBill b : page.getContent()) {
                        writer.write(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                                csvEscape(b.getBillNumber()),
                                csvEscape(b.getTableNumber()),
                                b.getStatus().name(),
                                b.getIssuedAt().toString(),
                                b.getSubtotal().toPlainString(),
                                b.getDiscountAmount().toPlainString(),
                                b.getServiceChargeAmount().toPlainString(),
                                b.getTaxAmount().toPlainString(),
                                b.getGrandTotal().toPlainString(),
                                b.getPaidAmount().toPlainString(),
                                b.getOutstandingBalance().toPlainString(),
                                b.getCurrencyCode()));
                    }
                    writer.flush();
                } while (page.hasNext());
            }
        }
    }

    /**
     * Generates an executive A4 PDF financial report for the authorized scope.
     */
    @Transactional(readOnly = true)
    public byte[] generatePdfExport(DineFlowPrincipal principal,
                                    ReportPeriodType periodType,
                                    LocalDate fromDate,
                                    LocalDate toDate,
                                    UUID requestedBranchId,
                                    String timezone,
                                    Integer businessDayCutoffHour) {
        FinancialReportResponse report = generateFinancialReport(
                principal, periodType, fromDate, toDate, requestedBranchId, timezone, businessDayCutoffHour);
        String restaurantName = tenantRepository.findById(principal.getTenantId())
                .map(Tenant::getBusinessName)
                .orElse("DineFlow Restaurant");
        return pdfGenerator.generatePdf(report, restaurantName);
    }

    private List<FinancialReportResponse.BranchSalesSummary> buildBranchBreakdown(UUID tenantId, UUID branchFilter, Instant start, Instant end) {
        Map<UUID, String> branchNames = new HashMap<>();
        branchRepository.findAllByTenantId(tenantId).forEach(b -> branchNames.put(b.getId(), b.getName()));

        Map<UUID, BigDecimal> paymentsByBranch = new HashMap<>();
        for (Object[] row : paymentRepository.aggregateConfirmedPaymentsByBranch(tenantId, branchFilter, start, end)) {
            paymentsByBranch.put((UUID) row[0], toMoney(row[1]));
        }

        Map<UUID, BigDecimal> refundsByBranch = new HashMap<>();
        for (Object[] row : refundRepository.aggregateSucceededRefundsByBranch(tenantId, branchFilter, start, end)) {
            refundsByBranch.put((UUID) row[0], toMoney(row[1]));
        }

        List<FinancialReportResponse.BranchSalesSummary> result = new ArrayList<>();
        for (Object[] row : billRepository.aggregateBillsByBranch(tenantId, branchFilter, start, end)) {
            UUID bId = (UUID) row[0];
            String snapshotName = (String) row[1];
            String resolvedName = bId != null ? branchNames.getOrDefault(bId, snapshotName != null ? snapshotName : "Branch " + bId.toString().substring(0, 6)) : "Default / Main";
            long bCount = toLong(row[2]);
            BigDecimal bGross = toMoney(row[3]);
            BigDecimal bDisc = toMoney(row[4]);
            BigDecimal bNet = bGross.subtract(bDisc).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal bTotal = toMoney(row[5]);
            BigDecimal bOutstanding = toMoney(row[6]);
            BigDecimal bCollected = paymentsByBranch.getOrDefault(bId, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            BigDecimal bRefunded = refundsByBranch.getOrDefault(bId, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            BigDecimal bNetCollected = bCollected.subtract(bRefunded).setScale(2, RoundingMode.HALF_UP);

            result.add(new FinancialReportResponse.BranchSalesSummary(
                    bId, resolvedName, bCount, bGross, bDisc, bNet, bTotal, bCollected, bRefunded, bNetCollected, bOutstanding));
        }
        return result;
    }

    private UUID resolveAuthorizedBranch(DineFlowPrincipal principal, UUID requestedBranchId) {
        if (principal.getBranchId() != null) {
            if (requestedBranchId != null && !requestedBranchId.equals(principal.getBranchId())) {
                throw new BusinessException("Branch managers can only access financial reports for their assigned branch",
                        HttpStatus.FORBIDDEN, "CROSS_BRANCH_ACCESS");
            }
            return principal.getBranchId();
        }
        return requestedBranchId;
    }

    private Map<String, String> buildFormulaDefinitions() {
        Map<String, String> defs = new LinkedHashMap<>();
        defs.put("grossSales", "SUM(bill.subtotal) for non-VOIDED bills issued in window");
        defs.put("netSales", "grossSales - discountTotal (pre-tax, pre-service-charge revenue)");
        defs.put("totalBillValue", "SUM(bill.grandTotal) = netSales + serviceChargeTotal + taxTotal");
        defs.put("averageBillValue", "totalBillValue / billCount (0.00 when billCount = 0)");
        defs.put("confirmedCollectionTotal", "SUM(payment.paidAmount) strictly for SUCCEEDED, PARTIALLY_REFUNDED, or REFUNDED payments settled in window (excludes PENDING, INITIATED, FAILED, CANCELLED)");
        defs.put("refundTotal", "SUM(refund.refundAmount) strictly for SUCCEEDED refunds settled in window");
        defs.put("netConfirmedCollection", "confirmedCollectionTotal - refundTotal");
        defs.put("outstandingBalance", "SUM(bill.outstandingBalance) for ISSUED and PARTIALLY_PAID bills in window");
        return defs;
    }

    private static class TrendAcc {
        long count = 0;
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;

        void add(BigDecimal sub, BigDecimal disc, BigDecimal gt) {
            count++;
            gross = gross.add(sub);
            discount = discount.add(disc);
            total = total.add(gt);
        }

        FinancialReportResponse.SalesTrendPoint toPoint(String label) {
            BigDecimal g = gross.setScale(2, RoundingMode.HALF_UP);
            BigDecimal n = gross.subtract(discount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal t = total.setScale(2, RoundingMode.HALF_UP);
            return new FinancialReportResponse.SalesTrendPoint(label, count, g, n, t);
        }
    }

    private Object[] unwrapSingleRow(Object[] raw) {
        if (raw != null && raw.length == 1 && raw[0] instanceof Object[] nested) {
            return nested;
        }
        return raw;
    }

    private long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number n) return n.longValue();
        return Long.parseLong(val.toString());
    }

    private BigDecimal toMoney(Object val) {
        if (val == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (val instanceof BigDecimal bd) return bd.setScale(2, RoundingMode.HALF_UP);
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        return new BigDecimal(val.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
