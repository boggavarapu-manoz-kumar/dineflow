package com.dineflow.payment;

import com.dineflow.billing.domain.BillStatus;
import com.dineflow.billing.domain.RestaurantBill;
import com.dineflow.billing.repository.RestaurantBillRepository;
import com.dineflow.billing.service.BillingEngineService;
import com.dineflow.branch.domain.Branch;
import com.dineflow.branch.repository.BranchRepository;
import com.dineflow.common.exception.BusinessException;
import com.dineflow.invoice.service.DocumentStorageService;
import com.dineflow.payment.domain.PaymentMethod;
import com.dineflow.payment.domain.PaymentProviderType;
import com.dineflow.payment.domain.PaymentStatus;
import com.dineflow.payment.domain.RefundMethod;
import com.dineflow.payment.dto.InitiatePaymentRequest;
import com.dineflow.payment.dto.PaymentResponse;
import com.dineflow.payment.dto.RecordCashPaymentRequest;
import com.dineflow.payment.dto.RefundResponse;
import com.dineflow.payment.dto.RequestRefundRequest;
import com.dineflow.payment.dto.ReversePaymentRequest;
import com.dineflow.payment.dto.VerifyPaymentRequest;
import com.dineflow.payment.repository.RestaurantPaymentRepository;
import com.dineflow.payment.service.PaymentProcessingService;
import com.dineflow.payment.service.RefundManagementService;
import com.dineflow.promotion.domain.DiscountType;
import com.dineflow.promotion.domain.PromotionScope;
import com.dineflow.promotion.dto.CouponApplicationResponse;
import com.dineflow.promotion.dto.CreatePromotionRequest;
import com.dineflow.promotion.service.PromotionEngineService;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 5 Prompt 12B Security Remediation & Independent Financial Calculation Verification Suite.
 */
@SpringBootTest
@ActiveProfiles("test")
public class Sprint5SecurityAndFinancialAuditTests {

    @Autowired private PaymentProcessingService paymentService;
    @Autowired private RefundManagementService refundService;
    @Autowired private BillingEngineService billingService;
    @Autowired private PromotionEngineService promotionService;
    @Autowired private FinancialReportingService reportingService;
    @Autowired private DocumentStorageService documentStorageService;
    @Autowired private RestaurantBillRepository billRepository;
    @Autowired private RestaurantPaymentRepository paymentRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private TenantRepository tenantRepository;

    private Tenant tenant;
    private Branch branch1;
    private Branch branch2;
    private UUID guestSession1;
    private UUID guestSession2;
    private DineFlowPrincipal guest1;
    private DineFlowPrincipal guest2;
    private DineFlowPrincipal admin;
    private DineFlowPrincipal branch1Manager;
    private DineFlowPrincipal branch2Manager;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setBusinessName("Audit Verification Hotel");
        tenant.setSlug("audit-hotel-" + UUID.randomUUID());
        tenant.setStatus("ACTIVE");
        tenant.setSubscriptionPlan("ENTERPRISE");
        tenant = tenantRepository.save(tenant);

        branch1 = new Branch("Branch One", "B1-" + UUID.randomUUID().toString().substring(0, 4), "Addr 1", "+91-1", true, true);
        branch1.setTenantId(tenant.getId());
        branch1 = branchRepository.save(branch1);

        branch2 = new Branch("Branch Two", "B2-" + UUID.randomUUID().toString().substring(0, 4), "Addr 2", "+91-2", true, true);
        branch2.setTenantId(tenant.getId());
        branch2 = branchRepository.save(branch2);

        guestSession1 = UUID.randomUUID();
        guestSession2 = UUID.randomUUID();

        guest1 = new DineFlowPrincipal(guestSession1, tenant.getId(), branch1.getId(), "GUEST", List.of("CUSTOMER_ORDER_VIEW_OWN"), "GUEST");
        guest2 = new DineFlowPrincipal(guestSession2, tenant.getId(), branch1.getId(), "GUEST", List.of("CUSTOMER_ORDER_VIEW_OWN"), "GUEST");
        admin = new DineFlowPrincipal(UUID.randomUUID(), tenant.getId(), null, "HOTEL_ADMIN",
                List.of("BILL_VIEW", "PAYMENT_VIEW", "PAYMENT_PROCESS", "REFUND_REQUEST", "REFUND_APPROVE", "REFUND_EXECUTE",
                        "PROMOTION_MANAGE", "PROMOTION_APPLY", "REPORT_FINANCIAL_VIEW"), "STAFF");
        branch1Manager = new DineFlowPrincipal(UUID.randomUUID(), tenant.getId(), branch1.getId(), "BRANCH_MANAGER",
                List.of("BILL_VIEW", "PAYMENT_VIEW", "PAYMENT_PROCESS", "REFUND_REQUEST", "REFUND_APPROVE", "REFUND_EXECUTE"), "STAFF");
        branch2Manager = new DineFlowPrincipal(UUID.randomUUID(), tenant.getId(), branch2.getId(), "BRANCH_MANAGER",
                List.of("BILL_VIEW", "PAYMENT_VIEW", "PAYMENT_PROCESS", "REFUND_REQUEST", "REFUND_APPROVE", "REFUND_EXECUTE"), "STAFF");
    }

    private RestaurantBill createBill(UUID branchId, UUID sessionId, BigDecimal subtotal, BigDecimal scPct, BigDecimal taxPct) {
        RestaurantBill bill = new RestaurantBill();
        bill.setTenantId(tenant.getId());
        bill.setBranchId(branchId);
        bill.setCustomerSessionId(sessionId);
        bill.setBillNumber("BILL-" + UUID.randomUUID().toString().substring(0, 8));
        bill.setTableNumber("T-09");
        bill.setRestaurantNameSnapshot(tenant.getBusinessName());
        bill.setCurrencyCode("INR");
        bill.setSubtotal(subtotal);
        bill.setDiscountAmount(BigDecimal.ZERO);
        bill.setServiceChargePercentage(scPct);
        bill.setTaxPercentage(taxPct);
        BigDecimal sc = subtotal.multiply(scPct).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal tax = subtotal.add(sc).multiply(taxPct).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal gt = subtotal.add(sc).add(tax);
        bill.setServiceChargeAmount(sc);
        bill.setTaxAmount(tax);
        bill.setGrandTotal(gt);
        bill.setPaidAmount(BigDecimal.ZERO);
        bill.setOutstandingBalance(gt);
        bill.setStatus(BillStatus.ISSUED);
        return billRepository.save(bill);
    }

    @Test
    @DisplayName("S5-AUD-001: Guest CANNOT self-settle a CASH or PAY_AT_COUNTER payment")
    void guestCannotSettleCashPayment() {
        RestaurantBill bill = createBill(branch1.getId(), guestSession1, new BigDecimal("500.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        PaymentResponse initiated = paymentService.initiatePayment(guest1,
                new InitiatePaymentRequest(bill.getId(), PaymentMethod.CASH, PaymentProviderType.CASH_REGISTER, null, "Pay cash", null));

        assertThatThrownBy(() -> paymentService.settlePayment(guest1, initiated.id(),
                new VerifyPaymentRequest("FAKE-CASH-TXN", null, "FAKE")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only authorized restaurant staff");
    }

    @Test
    @DisplayName("S5-AUD-002: payment.failed webhook & currency mismatch webhook NEVER settle a bill")
    void webhookEventTypeAndCurrencyValidationEnforced() {
        RestaurantBill bill = createBill(branch1.getId(), guestSession1, new BigDecimal("500.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        PaymentResponse init = paymentService.initiatePayment(guest1,
                new InitiatePaymentRequest(bill.getId(), PaymentMethod.ONLINE_PAYMENT, PaymentProviderType.MOCK, null, null, null));

        // 1. payment.failed webhook must mark payment FAILED, NOT SUCCEEDED
        String failedPayload = String.format(
                "{\"event\":\"payment.failed\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_fail_1\",\"order_id\":\"%s\",\"amount\":50000,\"currency\":\"INR\"}}}}",
                init.providerOrderId());
        paymentService.processProviderWebhook("MOCK", failedPayload, Map.of("x-razorpay-signature", "ok", "x-razorpay-event-id", "evt_fail_" + UUID.randomUUID()));

        PaymentResponse afterFail = paymentService.getPayment(admin, init.id());
        assertThat(afterFail.paymentStatus()).isEqualTo(PaymentStatus.FAILED.name());
        assertThat(billRepository.findById(bill.getId()).orElseThrow().getStatus()).isEqualTo(BillStatus.ISSUED);

        // 2. Currency mismatch (USD vs INR) on payment.captured must NOT settle bill
        RestaurantBill bill2 = createBill(branch1.getId(), guestSession1, new BigDecimal("500.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        PaymentResponse init2 = paymentService.initiatePayment(guest1,
                new InitiatePaymentRequest(bill2.getId(), PaymentMethod.ONLINE_PAYMENT, PaymentProviderType.MOCK, null, null, null));
        String wrongCurrencyPayload = String.format(
                "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_usd_1\",\"order_id\":\"%s\",\"amount\":50000,\"currency\":\"USD\"}}}}",
                init2.providerOrderId());
        paymentService.processProviderWebhook("MOCK", wrongCurrencyPayload, Map.of("x-razorpay-signature", "ok", "x-razorpay-event-id", "evt_usd_" + UUID.randomUUID()));

        assertThat(paymentService.getPayment(admin, init2.id()).paymentStatus()).isEqualTo(PaymentStatus.INITIATED.name());
        assertThat(billRepository.findById(bill2.getId()).orElseThrow().getStatus()).isEqualTo(BillStatus.ISSUED);
    }

    @Test
    @DisplayName("S5-AUD-004 & S5-AUD-008: Cross-guest IDOR and cross-branch refund/reversal are strictly blocked")
    void crossGuestIdorAndCrossBranchRefundBlocked() {
        RestaurantBill guest1Bill = createBill(branch1.getId(), guestSession1, new BigDecimal("600.00"), BigDecimal.ZERO, BigDecimal.ZERO);

        // Guest 2 cannot view Guest 1's bill or initiate payment for it
        assertThatThrownBy(() -> billingService.getBill(guest2, guest1Bill.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unauthorized cross-guest");

        assertThatThrownBy(() -> paymentService.initiatePayment(guest2,
                new InitiatePaymentRequest(guest1Bill.getId(), PaymentMethod.ONLINE_PAYMENT, PaymentProviderType.MOCK, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unauthorized cross-guest");

        // Branch 1 manager settles bill, Branch 2 manager CANNOT reverse or refund it
        PaymentResponse cashPay = paymentService.recordStaffCashPayment(branch1Manager,
                new RecordCashPaymentRequest(guest1Bill.getId(), new BigDecimal("600.00"), "Paid at B1"));

        assertThatThrownBy(() -> paymentService.reversePayment(branch2Manager, cashPay.id(), new ReversePaymentRequest("Wrong branch")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cross-branch");

        assertThatThrownBy(() -> refundService.requestRefund(branch2Manager,
                new RequestRefundRequest(cashPay.id(), new BigDecimal("100.00"), RefundMethod.CASH, "Cross branch attempt")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cross-branch");
    }

    @Test
    @DisplayName("S5-AUD-010: DocumentStorageService blocks cross-tenant storageKey access")
    void documentStorageBlocksCrossTenantKey() {
        UUID tenantX = UUID.randomUUID();
        UUID tenantY = UUID.randomUUID();
        var stored = documentStorageService.storeDocument(tenantX, UUID.randomUUID(), "INV-TEST.pdf", "%PDF-1.4 test".getBytes());

        assertThat(documentStorageService.loadDocumentAsResource(tenantX, stored.storageKey())).isPresent();
        assertThat(documentStorageService.loadDocumentAsResource(tenantY, stored.storageKey())).isEmpty();
    }

    @Test
    @DisplayName("Phase 4 Independent Financial Calculation: Discount + Service Charge + Tax + Split Payments + Partial Refunds + Report")
    void independentEndToEndFinancialCalculationCheck() {
        // Subtotal = 1200.00, Service Charge = 5%, Tax = 10%
        // Apply 10% coupon (pre-tax) -> Discount = 120.00
        // Net Subtotal = 1080.00
        // Service Charge (5% of 1080.00) = 54.00
        // Taxable Base = 1080.00 + 54.00 = 1134.00
        // Tax (10% of 1134.00) = 113.40
        // Expected Grand Total = 1134.00 + 113.40 = 1247.40
        promotionService.createPromotion(admin, new CreatePromotionRequest(
                "Audit 10% Off", "Pre-tax 10%", null, "AUDIT10", DiscountType.PERCENTAGE, new BigDecimal("10.00"),
                PromotionScope.ORDER_LEVEL, BigDecimal.ZERO, null,
                Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofDays(1)),
                null, null, true, false, true, null, null));

        RestaurantBill bill = createBill(branch1.getId(), guestSession1, new BigDecimal("1200.00"), new BigDecimal("5.00"), new BigDecimal("10.00"));
        CouponApplicationResponse discounted = promotionService.applyCouponToBill(admin, bill.getId(), "AUDIT10");

        assertThat(discounted.discountAmount()).isEqualByComparingTo("120.00");
        assertThat(discounted.serviceChargeAmount()).isEqualByComparingTo("54.00");
        assertThat(discounted.taxAmount()).isEqualByComparingTo("113.40");
        assertThat(discounted.grandTotal()).isEqualByComparingTo("1247.40");

        // Split payment 1: 500.00 cash -> remaining 747.40
        PaymentResponse pay1 = paymentService.recordStaffCashPayment(admin,
                new RecordCashPaymentRequest(bill.getId(), new BigDecimal("500.00"), "Split 1"));
        assertThat(billRepository.findById(bill.getId()).orElseThrow().getOutstandingBalance()).isEqualByComparingTo("747.40");

        // Split payment 2: 747.40 cash -> remaining 0.00 (PAID)
        paymentService.recordStaffCashPayment(admin,
                new RecordCashPaymentRequest(bill.getId(), new BigDecimal("747.40"), "Split 2"));
        assertThat(billRepository.findById(bill.getId()).orElseThrow().getStatus()).isEqualTo(BillStatus.PAID);

        // Two partial refunds against pay1: 150.00 + 100.00 = 250.00
        RefundResponse ref1 = refundService.requestRefund(admin, new RequestRefundRequest(pay1.id(), new BigDecimal("150.00"), RefundMethod.CASH, "Partial 1"));
        refundService.executeRefund(admin, ref1.id());
        RefundResponse ref2 = refundService.requestRefund(admin, new RequestRefundRequest(pay1.id(), new BigDecimal("100.00"), RefundMethod.CASH, "Partial 2"));
        refundService.executeRefund(admin, ref2.id());

        // Financial Report must show: Gross 1200, Disc 120, NetSales 1080, Confirmed 1247.40, Refunds 250.00, NetCollected 997.40
        LocalDate todayIst = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        FinancialReportResponse report = reportingService.generateFinancialReport(admin, ReportPeriodType.DAILY, todayIst, todayIst, null, "Asia/Kolkata", 0);
        assertThat(report.grossSales()).isEqualByComparingTo("1200.00");
        assertThat(report.discountTotal()).isEqualByComparingTo("120.00");
        assertThat(report.netSales()).isEqualByComparingTo("1080.00");
        assertThat(report.confirmedCollectionTotal()).isEqualByComparingTo("1247.40");
        assertThat(report.refundTotal()).isEqualByComparingTo("250.00");
        assertThat(report.netConfirmedCollection()).isEqualByComparingTo("997.40");
    }
}
