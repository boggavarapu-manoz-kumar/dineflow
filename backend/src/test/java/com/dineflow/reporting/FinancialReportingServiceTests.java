package com.dineflow.reporting;

import com.dineflow.billing.domain.BillStatus;
import com.dineflow.billing.domain.RestaurantBill;
import com.dineflow.billing.repository.RestaurantBillRepository;
import com.dineflow.branch.domain.Branch;
import com.dineflow.branch.repository.BranchRepository;
import com.dineflow.common.exception.BusinessException;
import com.dineflow.payment.domain.PaymentMethod;
import com.dineflow.payment.domain.PaymentProviderType;
import com.dineflow.payment.domain.PaymentStatus;
import com.dineflow.payment.domain.RefundMethod;
import com.dineflow.payment.domain.RefundStatus;
import com.dineflow.payment.domain.RestaurantPayment;
import com.dineflow.payment.domain.RestaurantRefund;
import com.dineflow.payment.repository.PaymentWebhookEventRepository;
import com.dineflow.payment.repository.RestaurantPaymentRepository;
import com.dineflow.payment.repository.RestaurantRefundRepository;
import com.dineflow.payment.service.PaymentProcessingService;
import com.dineflow.reporting.dto.FinancialReportResponse;
import com.dineflow.reporting.dto.ReportPeriodType;
import com.dineflow.reporting.service.FinancialReportingService;
import com.dineflow.security.DineFlowPrincipal;
import com.dineflow.tenant.domain.Tenant;
import com.dineflow.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
public class FinancialReportingServiceTests {

    @Autowired
    private FinancialReportingService reportingService;

    @Autowired
    private PaymentProcessingService paymentService;

    @Autowired
    private RestaurantBillRepository billRepository;

    @Autowired
    private RestaurantPaymentRepository paymentRepository;

    @Autowired
    private RestaurantRefundRepository refundRepository;

    @Autowired
    private PaymentWebhookEventRepository webhookEventRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private Tenant tenantA;
    private Tenant tenantB;
    private Branch branchA1;
    private Branch branchA2;
    private DineFlowPrincipal adminA;
    private DineFlowPrincipal managerBranchA1;
    private DineFlowPrincipal adminB;

    @BeforeEach
    void setUp() {
        tenantA = new Tenant();
        tenantA.setBusinessName("Reporting Palace A");
        tenantA.setSlug("report-tenant-a-" + UUID.randomUUID());
        tenantA.setStatus("ACTIVE");
        tenantA.setSubscriptionPlan("ENTERPRISE");
        tenantA = tenantRepository.save(tenantA);

        tenantB = new Tenant();
        tenantB.setBusinessName("Reporting Palace B");
        tenantB.setSlug("report-tenant-b-" + UUID.randomUUID());
        tenantB.setStatus("ACTIVE");
        tenantB.setSubscriptionPlan("ENTERPRISE");
        tenantB = tenantRepository.save(tenantB);

        branchA1 = new Branch("Main Lounge", "ML-" + UUID.randomUUID().toString().substring(0, 4), "Addr 1", "+91-1", true, true);
        branchA1.setTenantId(tenantA.getId());
        branchA1 = branchRepository.save(branchA1);

        branchA2 = new Branch("Rooftop Deck", "RD-" + UUID.randomUUID().toString().substring(0, 4), "Addr 2", "+91-2", true, true);
        branchA2.setTenantId(tenantA.getId());
        branchA2 = branchRepository.save(branchA2);

        adminA = new DineFlowPrincipal(UUID.randomUUID(), tenantA.getId(), null, "HOTEL_ADMIN",
                List.of("REPORT_FINANCIAL_VIEW", "REPORT_FINANCIAL_EXPORT"), "STAFF");
        managerBranchA1 = new DineFlowPrincipal(UUID.randomUUID(), tenantA.getId(), branchA1.getId(), "BRANCH_MANAGER",
                List.of("REPORT_FINANCIAL_VIEW", "REPORT_FINANCIAL_EXPORT"), "STAFF");
        adminB = new DineFlowPrincipal(UUID.randomUUID(), tenantB.getId(), null, "HOTEL_ADMIN",
                List.of("REPORT_FINANCIAL_VIEW", "REPORT_FINANCIAL_EXPORT"), "STAFF");
    }

    private RestaurantBill seedBill(UUID tenantId, UUID branchId, Instant issuedAt, BigDecimal subtotal,
                                    BigDecimal discount, BigDecimal sc, BigDecimal tax, BigDecimal paid, BillStatus status) {
        RestaurantBill bill = new RestaurantBill();
        bill.setTenantId(tenantId);
        bill.setBranchId(branchId);
        bill.setBillNumber("BILL-" + UUID.randomUUID().toString().substring(0, 8));
        bill.setTableNumber("T-1");
        bill.setRestaurantNameSnapshot("Reporting Palace");
        bill.setCurrencyCode("INR");
        bill.setSubtotal(subtotal);
        bill.setDiscountAmount(discount);
        bill.setServiceChargeAmount(sc);
        bill.setTaxAmount(tax);
        BigDecimal grandTotal = subtotal.subtract(discount).add(sc).add(tax);
        bill.setGrandTotal(grandTotal);
        bill.setPaidAmount(paid);
        bill.setOutstandingBalance(grandTotal.subtract(paid).max(BigDecimal.ZERO));
        bill.setStatus(status);
        bill.setIssuedAt(issuedAt);
        return billRepository.save(bill);
    }

    private RestaurantPayment seedPayment(RestaurantBill bill, PaymentMethod method, PaymentStatus status,
                                          BigDecimal amount, Instant settledAt) {
        RestaurantPayment p = new RestaurantPayment(
                bill.getTenantId(), bill.getBranchId(), bill,
                "PAY-" + UUID.randomUUID().toString().substring(0, 8),
                method, amount, "INR",
                method == PaymentMethod.ONLINE_PAYMENT ? PaymentProviderType.RAZORPAY : PaymentProviderType.CASH_REGISTER
        );
        p.setPaymentStatus(status);
        if (status == PaymentStatus.SUCCEEDED || status == PaymentStatus.PARTIALLY_REFUNDED || status == PaymentStatus.REFUNDED) {
            p.setPaidAmount(amount);
            p.setSettledAt(settledAt);
        }
        return paymentRepository.save(p);
    }

    @Test
    @DisplayName("Daily/Monthly report accurately excludes FAILED/PENDING payments and deducts SUCCEEDED refunds")
    void testAccurateFinancialCalculationsAndExclusions() {
        LocalDate bizDay = LocalDate.of(2026, 11, 20);
        Instant noonIst = ZonedDateTime.of(bizDay, java.time.LocalTime.NOON, ZoneId.of("Asia/Kolkata")).toInstant();

        // Bill 1: Paid 1050 via ONLINE (Subtotal 1000, Disc 100, SC 50, Tax 100 -> GT 1050)
        RestaurantBill b1 = seedBill(tenantA.getId(), branchA1.getId(), noonIst,
                new BigDecimal("1000.00"), new BigDecimal("100.00"), new BigDecimal("50.00"), new BigDecimal("100.00"),
                new BigDecimal("1050.00"), BillStatus.PAID);
        RestaurantPayment p1 = seedPayment(b1, PaymentMethod.ONLINE_PAYMENT, PaymentStatus.SUCCEEDED, new BigDecimal("1050.00"), noonIst);

        // Bill 2: Unpaid ISSUED bill (Subtotal 500 -> GT 500), with a FAILED and an INITIATED payment that MUST be excluded!
        RestaurantBill b2 = seedBill(tenantA.getId(), branchA1.getId(), noonIst,
                new BigDecimal("500.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BillStatus.ISSUED);
        seedPayment(b2, PaymentMethod.ONLINE_PAYMENT, PaymentStatus.FAILED, new BigDecimal("500.00"), noonIst);
        seedPayment(b2, PaymentMethod.ONLINE_PAYMENT, PaymentStatus.INITIATED, new BigDecimal("500.00"), noonIst);

        // Bill 3: VOIDED bill MUST NOT be included in sales
        seedBill(tenantA.getId(), branchA1.getId(), noonIst,
                new BigDecimal("999.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BillStatus.VOIDED);

        // Refund of 200.00 against p1
        RestaurantRefund refund = new RestaurantRefund(tenantA.getId(), branchA1.getId(), p1, b1,
                "REF-001", new BigDecimal("200.00"), "INR", RefundMethod.ONLINE_GATEWAY, "Partial refund", UUID.randomUUID());
        refund.setRefundStatus(RefundStatus.SUCCEEDED);
        refund.setSettledAt(noonIst);
        refundRepository.save(refund);

        // Duplicate webhook event must not inflate revenue
        Map<String, String> whHeaders = Map.of("x-razorpay-signature", "sig", "x-razorpay-event-id", "evt_rep_1");
        String payload = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_none\",\"amount\":105000,\"currency\":\"INR\"}}}}";
        paymentService.processProviderWebhook("MOCK", payload, whHeaders);
        paymentService.processProviderWebhook("MOCK", payload, whHeaders);

        FinancialReportResponse report = reportingService.generateFinancialReport(
                adminA, ReportPeriodType.DAILY, bizDay, bizDay, null, "Asia/Kolkata", 0);

        assertThat(report.billCount()).isEqualTo(2);
        assertThat(report.grossSales()).isEqualByComparingTo("1500.00");
        assertThat(report.discountTotal()).isEqualByComparingTo("100.00");
        assertThat(report.netSales()).isEqualByComparingTo("1400.00");
        assertThat(report.serviceChargeTotal()).isEqualByComparingTo("50.00");
        assertThat(report.taxTotal()).isEqualByComparingTo("100.00");
        assertThat(report.totalBillValue()).isEqualByComparingTo("1550.00");
        assertThat(report.averageBillValue()).isEqualByComparingTo("775.00");
        assertThat(report.outstandingBalance()).isEqualByComparingTo("500.00");
        assertThat(report.confirmedCollectionTotal()).isEqualByComparingTo("1050.00");
        assertThat(report.onlineCollectionTotal()).isEqualByComparingTo("1050.00");
        assertThat(report.refundTotal()).isEqualByComparingTo("200.00");
        assertThat(report.netConfirmedCollection()).isEqualByComparingTo("850.00");
    }

    @Test
    @DisplayName("Timezone boundaries and cross-midnight business day cutoff (04:00 AM) assign late-night bills accurately")
    void testTimezoneAndCrossMidnightCutoff() {
        // 01:30 AM IST on Nov 21 = 20:00 UTC on Nov 20
        // With Asia/Kolkata + cutoffHour=4: belongs to business day Nov 20!
        // With Asia/Kolkata + cutoffHour=0: belongs to business day Nov 21!
        Instant lateNightIst = ZonedDateTime.of(2026, 11, 21, 1, 30, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant();
        seedBill(tenantA.getId(), branchA1.getId(), lateNightIst,
                new BigDecimal("400.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("400.00"), BillStatus.PAID);

        FinancialReportResponse withCutoff4 = reportingService.generateFinancialReport(
                adminA, ReportPeriodType.DAILY, LocalDate.of(2026, 11, 20), null, null, "Asia/Kolkata", 4);
        assertThat(withCutoff4.billCount()).isEqualTo(1);
        assertThat(withCutoff4.grossSales()).isEqualByComparingTo("400.00");

        FinancialReportResponse withCutoff0 = reportingService.generateFinancialReport(
                adminA, ReportPeriodType.DAILY, LocalDate.of(2026, 11, 20), null, null, "Asia/Kolkata", 0);
        assertThat(withCutoff0.billCount()).isEqualTo(0);
        assertThat(withCutoff0.grossSales()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Tenant and branch isolation strictly enforced in reports and exports")
    void testTenantAndBranchIsolation() {
        LocalDate day = LocalDate.of(2026, 11, 22);
        Instant t = ZonedDateTime.of(day, java.time.LocalTime.NOON, ZoneId.of("Asia/Kolkata")).toInstant();

        seedBill(tenantA.getId(), branchA1.getId(), t, new BigDecimal("300.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("300.00"), BillStatus.PAID);
        seedBill(tenantA.getId(), branchA2.getId(), t, new BigDecimal("700.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("700.00"), BillStatus.PAID);
        seedBill(tenantB.getId(), null, t, new BigDecimal("5000.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("5000.00"), BillStatus.PAID);

        // Tenant A admin sees both branches (300 + 700 = 1000), never Tenant B (5000)
        FinancialReportResponse reportA = reportingService.generateFinancialReport(adminA, ReportPeriodType.DAILY, day, day, null, "Asia/Kolkata", 0);
        assertThat(reportA.grossSales()).isEqualByComparingTo("1000.00");
        assertThat(reportA.branchBreakdown()).hasSize(2);

        // Branch A1 manager sees only Branch A1 (300)
        FinancialReportResponse reportBranch1 = reportingService.generateFinancialReport(managerBranchA1, ReportPeriodType.DAILY, day, day, null, "Asia/Kolkata", 0);
        assertThat(reportBranch1.grossSales()).isEqualByComparingTo("300.00");

        // Branch A1 manager attempting to query Branch A2 is rejected with 403
        assertThatThrownBy(() -> reportingService.generateFinancialReport(managerBranchA1, ReportPeriodType.DAILY, day, day, branchA2.getId(), "Asia/Kolkata", 0))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("assigned branch");

        // Tenant B admin sees only Tenant B (5000)
        FinancialReportResponse reportB = reportingService.generateFinancialReport(adminB, ReportPeriodType.DAILY, day, day, null, "Asia/Kolkata", 0);
        assertThat(reportB.grossSales()).isEqualByComparingTo("5000.00");
    }

    @Test
    @DisplayName("Streaming CSV export and PDF export produce accurate, tenant-isolated documents for large result sets")
    void testCsvAndPdfExports() throws Exception {
        LocalDate day = LocalDate.of(2026, 11, 23);
        Instant t = ZonedDateTime.of(day, java.time.LocalTime.NOON, ZoneId.of("Asia/Kolkata")).toInstant();

        // Seed 25 bills for tenantA and 3 for tenantB
        for (int i = 0; i < 25; i++) {
            seedBill(tenantA.getId(), branchA1.getId(), t.plusSeconds(i),
                    new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    new BigDecimal("100.00"), BillStatus.PAID);
        }
        for (int i = 0; i < 3; i++) {
            seedBill(tenantB.getId(), null, t.plusSeconds(i),
                    new BigDecimal("999.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    new BigDecimal("999.00"), BillStatus.PAID);
        }

        // 1. Streaming CSV Export
        ByteArrayOutputStream csvOut = new ByteArrayOutputStream();
        reportingService.streamCsvExport(adminA, ReportPeriodType.DAILY, day, day, null, null, "Asia/Kolkata", 0, csvOut);
        String csvText = csvOut.toString(StandardCharsets.UTF_8);
        String[] lines = csvText.trim().split("\n");
        // 1 header + 25 tenantA rows (zero tenantB rows!)
        assertThat(lines).hasSize(26);
        assertThat(csvText).doesNotContain("999.00");

        // 2. PDF Export
        byte[] pdfBytes = reportingService.generatePdfExport(adminA, ReportPeriodType.DAILY, day, day, null, "Asia/Kolkata", 0);
        assertThat(pdfBytes).isNotEmpty();
        assertThat(new String(pdfBytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }
}
