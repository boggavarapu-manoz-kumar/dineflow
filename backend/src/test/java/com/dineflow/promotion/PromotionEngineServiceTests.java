package com.dineflow.promotion;

import com.dineflow.billing.domain.BillStatus;
import com.dineflow.billing.domain.RestaurantBill;
import com.dineflow.billing.repository.RestaurantBillRepository;
import com.dineflow.branch.domain.Branch;
import com.dineflow.branch.repository.BranchRepository;
import com.dineflow.common.exception.BusinessException;
import com.dineflow.payment.domain.RefundMethod;
import com.dineflow.payment.dto.PaymentResponse;
import com.dineflow.payment.dto.RecordCashPaymentRequest;
import com.dineflow.payment.dto.RefundResponse;
import com.dineflow.payment.dto.RequestRefundRequest;
import com.dineflow.payment.repository.RestaurantPaymentRepository;
import com.dineflow.payment.repository.RestaurantRefundRepository;
import com.dineflow.payment.service.PaymentProcessingService;
import com.dineflow.payment.service.RefundManagementService;
import com.dineflow.promotion.domain.DiscountType;
import com.dineflow.promotion.domain.PromotionScope;
import com.dineflow.promotion.dto.CouponApplicationResponse;
import com.dineflow.promotion.dto.CreatePromotionRequest;
import com.dineflow.promotion.dto.PromotionResponse;
import com.dineflow.promotion.repository.PromotionRedemptionRepository;
import com.dineflow.promotion.repository.PromotionRepository;
import com.dineflow.promotion.service.PromotionEngineService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
public class PromotionEngineServiceTests {

    @Autowired
    private PromotionEngineService promotionService;

    @Autowired
    private PaymentProcessingService paymentService;

    @Autowired
    private RefundManagementService refundService;

    @Autowired
    private PromotionRedemptionRepository redemptionRepository;

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private RestaurantRefundRepository refundRepository;

    @Autowired
    private RestaurantPaymentRepository paymentRepository;

    @Autowired
    private RestaurantBillRepository billRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private Tenant tenantA;
    private Tenant tenantB;
    private Branch branchA1;
    private Branch branchA2;
    private DineFlowPrincipal adminA;
    private DineFlowPrincipal adminB;
    private UUID customerSessionId;

    @BeforeEach
    void setUp() {
        redemptionRepository.deleteAll();
        promotionRepository.deleteAll();
        refundRepository.deleteAll();
        paymentRepository.deleteAll();
        billRepository.deleteAll();

        tenantA = new Tenant();
        tenantA.setBusinessName("Grand Palace A");
        tenantA.setSlug("promo-tenant-a-" + UUID.randomUUID());
        tenantA.setStatus("ACTIVE");
        tenantA.setSubscriptionPlan("ENTERPRISE");
        tenantA = tenantRepository.save(tenantA);

        tenantB = new Tenant();
        tenantB.setBusinessName("Royal Orchid B");
        tenantB.setSlug("promo-tenant-b-" + UUID.randomUUID());
        tenantB.setStatus("ACTIVE");
        tenantB.setSubscriptionPlan("ENTERPRISE");
        tenantB = tenantRepository.save(tenantB);

        branchA1 = new Branch("Downtown Branch", "DT-" + UUID.randomUUID().toString().substring(0, 4), "Main St", "+91-111", true, true);
        branchA1.setTenantId(tenantA.getId());
        branchA1 = branchRepository.save(branchA1);

        branchA2 = new Branch("Airport Branch", "AP-" + UUID.randomUUID().toString().substring(0, 4), "Terminal 2", "+91-222", true, true);
        branchA2.setTenantId(tenantA.getId());
        branchA2 = branchRepository.save(branchA2);

        adminA = new DineFlowPrincipal(
                UUID.randomUUID(),
                tenantA.getId(),
                null,
                "HOTEL_ADMIN",
                List.of("PROMOTION_MANAGE", "PROMOTION_VIEW", "PROMOTION_APPLY", "PAYMENT_PROCESS", "REFUND_REQUEST", "REFUND_APPROVE", "REFUND_EXECUTE"),
                "STAFF"
        );

        adminB = new DineFlowPrincipal(
                UUID.randomUUID(),
                tenantB.getId(),
                null,
                "HOTEL_ADMIN",
                List.of("PROMOTION_MANAGE", "PROMOTION_VIEW", "PROMOTION_APPLY"),
                "STAFF"
        );

        customerSessionId = UUID.randomUUID();
    }

    private RestaurantBill createBill(UUID tenantId, UUID branchId, UUID sessionId, BigDecimal subtotal, BigDecimal taxPct, BigDecimal scPct) {
        RestaurantBill bill = new RestaurantBill();
        bill.setTenantId(tenantId);
        bill.setBranchId(branchId);
        bill.setCustomerSessionId(sessionId);
        bill.setBillNumber("BILL-" + UUID.randomUUID().toString().substring(0, 8));
        bill.setTableNumber("T-05");
        bill.setRestaurantNameSnapshot("Grand Palace");
        bill.setCurrencyCode("INR");
        bill.setSubtotal(subtotal);
        bill.setDiscountAmount(BigDecimal.ZERO);
        bill.setServiceChargePercentage(scPct);
        bill.setTaxPercentage(taxPct);
        BigDecimal sc = subtotal.multiply(scPct).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal tax = subtotal.add(sc).multiply(taxPct).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal grandTotal = subtotal.add(sc).add(tax);
        bill.setServiceChargeAmount(sc);
        bill.setTaxAmount(tax);
        bill.setGrandTotal(grandTotal);
        bill.setPaidAmount(BigDecimal.ZERO);
        bill.setOutstandingBalance(grandTotal);
        bill.setStatus(BillStatus.ISSUED);
        return billRepository.save(bill);
    }

    private CreatePromotionRequest basePromo(String code, DiscountType type, BigDecimal value) {
        return new CreatePromotionRequest(
                "Promo " + code,
                "Test promotion",
                null,
                code,
                type,
                value,
                PromotionScope.ORDER_LEVEL,
                BigDecimal.ZERO,
                null,
                Instant.now().minus(Duration.ofHours(1)),
                Instant.now().plus(Duration.ofDays(7)),
                null,
                null,
                true,
                false,
                true,
                null,
                null
        );
    }

    @Test
    @DisplayName("1. Fixed amount discount recalculates pre-tax bill totals accurately")
    void testFixedDiscount() {
        promotionService.createPromotion(adminA, basePromo("FLAT100", DiscountType.FIXED_AMOUNT, BigDecimal.valueOf(100.00)));
        RestaurantBill bill = createBill(tenantA.getId(), branchA1.getId(), customerSessionId, BigDecimal.valueOf(1000.00), BigDecimal.valueOf(10.00), BigDecimal.ZERO);

        CouponApplicationResponse res = promotionService.applyCouponToBill(adminA, bill.getId(), "flat100");

        assertThat(res.valid()).isTrue();
        assertThat(res.discountAmount()).isEqualByComparingTo("100.00");
        // Net subtotal = 900.00, 10% tax = 90.00, grand total = 990.00
        assertThat(res.taxAmount()).isEqualByComparingTo("90.00");
        assertThat(res.grandTotal()).isEqualByComparingTo("990.00");
        assertThat(res.outstandingBalance()).isEqualByComparingTo("990.00");
    }

    @Test
    @DisplayName("2. Percentage discount and 3. Maximum discount cap enforced server-side")
    void testPercentageAndMaxDiscountCap() {
        CreatePromotionRequest req = new CreatePromotionRequest(
                "20% Off Max 150",
                "Capped percentage promo",
                null,
                "SAVE20CAP",
                DiscountType.PERCENTAGE,
                BigDecimal.valueOf(20.00),
                PromotionScope.ORDER_LEVEL,
                BigDecimal.ZERO,
                BigDecimal.valueOf(150.00),
                Instant.now().minus(Duration.ofHours(1)),
                Instant.now().plus(Duration.ofDays(5)),
                null,
                null,
                true,
                false,
                true,
                null,
                null
        );
        promotionService.createPromotion(adminA, req);
        // 20% of 1000 = 200, must be capped to 150.00
        RestaurantBill bill = createBill(tenantA.getId(), branchA1.getId(), customerSessionId, BigDecimal.valueOf(1000.00), BigDecimal.ZERO, BigDecimal.ZERO);

        CouponApplicationResponse res = promotionService.applyCouponToBill(adminA, bill.getId(), "SAVE20CAP");
        assertThat(res.discountAmount()).isEqualByComparingTo("150.00");
        assertThat(res.grandTotal()).isEqualByComparingTo("850.00");
    }

    @Test
    @DisplayName("4. Expired coupon and 5. Future coupon are strictly rejected")
    void testExpiredAndFutureCouponsRejected() {
        CreatePromotionRequest expiredReq = new CreatePromotionRequest(
                "Expired Offer", null, null, "EXPIRED50", DiscountType.FIXED_AMOUNT, BigDecimal.valueOf(50),
                PromotionScope.ORDER_LEVEL, BigDecimal.ZERO, null,
                Instant.now().minus(Duration.ofDays(10)), Instant.now().minus(Duration.ofDays(1)),
                null, null, true, false, true, null, null
        );
        CreatePromotionRequest futureReq = new CreatePromotionRequest(
                "Future Offer", null, null, "FUTURE50", DiscountType.FIXED_AMOUNT, BigDecimal.valueOf(50),
                PromotionScope.ORDER_LEVEL, BigDecimal.ZERO, null,
                Instant.now().plus(Duration.ofDays(2)), Instant.now().plus(Duration.ofDays(10)),
                null, null, true, false, true, null, null
        );
        promotionService.createPromotion(adminA, expiredReq);
        promotionService.createPromotion(adminA, futureReq);

        RestaurantBill bill = createBill(tenantA.getId(), branchA1.getId(), customerSessionId, BigDecimal.valueOf(500.00), BigDecimal.ZERO, BigDecimal.ZERO);

        assertThatThrownBy(() -> promotionService.applyCouponToBill(adminA, bill.getId(), "EXPIRED50"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expired");

        assertThatThrownBy(() -> promotionService.applyCouponToBill(adminA, bill.getId(), "FUTURE50"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not yet active");
    }

    @Test
    @DisplayName("6. Wrong branch and 7. Wrong tenant isolation enforced")
    void testWrongBranchAndWrongTenantRejected() {
        CreatePromotionRequest branchPromo = new CreatePromotionRequest(
                "Downtown Only", null, branchA1.getId(), "DOWNTOWN10", DiscountType.PERCENTAGE, BigDecimal.valueOf(10),
                PromotionScope.ORDER_LEVEL, BigDecimal.ZERO, null,
                Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofDays(5)),
                null, null, true, false, true, null, null
        );
        promotionService.createPromotion(adminA, branchPromo);

        // Wrong branch (branchA2)
        RestaurantBill billBranch2 = createBill(tenantA.getId(), branchA2.getId(), customerSessionId, BigDecimal.valueOf(500.00), BigDecimal.ZERO, BigDecimal.ZERO);
        assertThatThrownBy(() -> promotionService.applyCouponToBill(adminA, billBranch2.getId(), "DOWNTOWN10"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not valid at this branch");

        // Wrong tenant (tenantB trying to redeem tenantA's coupon)
        RestaurantBill billTenantB = createBill(tenantB.getId(), null, customerSessionId, BigDecimal.valueOf(500.00), BigDecimal.ZERO, BigDecimal.ZERO);
        assertThatThrownBy(() -> promotionService.applyCouponToBill(adminB, billTenantB.getId(), "DOWNTOWN10"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid or ineligible coupon code");
    }

    @Test
    @DisplayName("8. Minimum order value requirement enforced")
    void testMinimumOrderValue() {
        CreatePromotionRequest minOrderPromo = new CreatePromotionRequest(
                "Min 1000", null, null, "MIN1000", DiscountType.FIXED_AMOUNT, BigDecimal.valueOf(100),
                PromotionScope.ORDER_LEVEL, BigDecimal.valueOf(1000.00), null,
                Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofDays(5)),
                null, null, true, false, true, null, null
        );
        promotionService.createPromotion(adminA, minOrderPromo);

        RestaurantBill smallBill = createBill(tenantA.getId(), branchA1.getId(), customerSessionId, BigDecimal.valueOf(750.00), BigDecimal.ZERO, BigDecimal.ZERO);
        assertThatThrownBy(() -> promotionService.applyCouponToBill(adminA, smallBill.getId(), "MIN1000"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Minimum order value");
    }

    @Test
    @DisplayName("9. Total usage limit and 10. Per-customer limit enforced")
    void testUsageLimitAndPerCustomerLimit() {
        CreatePromotionRequest perCustPromo = new CreatePromotionRequest(
                "Once Per Guest", null, null, "ONCEGUEST", DiscountType.FIXED_AMOUNT, BigDecimal.valueOf(50),
                PromotionScope.ORDER_LEVEL, BigDecimal.ZERO, null,
                Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofDays(5)),
                2, 1, true, false, true, null, null
        );
        promotionService.createPromotion(adminA, perCustPromo);

        RestaurantBill bill1 = createBill(tenantA.getId(), branchA1.getId(), customerSessionId, BigDecimal.valueOf(500.00), BigDecimal.ZERO, BigDecimal.ZERO);
        RestaurantBill bill2SameGuest = createBill(tenantA.getId(), branchA1.getId(), customerSessionId, BigDecimal.valueOf(500.00), BigDecimal.ZERO, BigDecimal.ZERO);
        RestaurantBill bill3OtherGuest = createBill(tenantA.getId(), branchA1.getId(), UUID.randomUUID(), BigDecimal.valueOf(500.00), BigDecimal.ZERO, BigDecimal.ZERO);
        RestaurantBill bill4ThirdGuest = createBill(tenantA.getId(), branchA1.getId(), UUID.randomUUID(), BigDecimal.valueOf(500.00), BigDecimal.ZERO, BigDecimal.ZERO);

        // First redemption succeeds (usage 1/2)
        promotionService.applyCouponToBill(adminA, bill1.getId(), "ONCEGUEST");

        // Same customer trying on second bill fails per-customer limit
        assertThatThrownBy(() -> promotionService.applyCouponToBill(adminA, bill2SameGuest.getId(), "ONCEGUEST"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("reached the usage limit");

        // Second distinct customer succeeds (usage 2/2)
        promotionService.applyCouponToBill(adminA, bill3OtherGuest.getId(), "ONCEGUEST");

        // Third distinct customer fails total usage limit
        assertThatThrownBy(() -> promotionService.applyCouponToBill(adminA, bill4ThirdGuest.getId(), "ONCEGUEST"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("usage limit has been reached");
    }

    @Test
    @DisplayName("11. Concurrent redemptions strictly respect atomic usage limit")
    void testConcurrentRedemptionRespectsUsageLimit() throws Exception {
        CreatePromotionRequest limitedPromo = new CreatePromotionRequest(
                "Flash 2 Slots", null, null, "FLASH2", DiscountType.FIXED_AMOUNT, BigDecimal.valueOf(50),
                PromotionScope.ORDER_LEVEL, BigDecimal.ZERO, null,
                Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofDays(1)),
                2, null, true, false, true, null, null
        );
        PromotionResponse created = promotionService.createPromotion(adminA, limitedPromo);

        int threads = 6;
        List<RestaurantBill> bills = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            bills.add(createBill(tenantA.getId(), branchA1.getId(), UUID.randomUUID(), BigDecimal.valueOf(500.00), BigDecimal.ZERO, BigDecimal.ZERO));
        }

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final UUID billId = bills.get(i).getId();
            tasks.add(() -> {
                ready.countDown();
                start.await();
                try {
                    promotionService.applyCouponToBill(adminA, billId, "FLASH2");
                    successes.incrementAndGet();
                } catch (Exception ex) {
                    failures.incrementAndGet();
                }
                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
            futures.add(pool.submit(task));
        }
        ready.await();
        start.countDown();
        for (Future<Void> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(successes.get()).isEqualTo(2);
        assertThat(failures.get()).isEqualTo(threads - 2);
        assertThat(promotionRepository.findById(created.id()).orElseThrow().getCurrentUsageCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("12. Duplicate redemption on same bill rejected, and removal restores usage & totals")
    void testDuplicateRedemptionAndRemoval() {
        PromotionResponse promo = promotionService.createPromotion(adminA, basePromo("NODUP", DiscountType.FIXED_AMOUNT, BigDecimal.valueOf(75)));
        RestaurantBill bill = createBill(tenantA.getId(), branchA1.getId(), customerSessionId, BigDecimal.valueOf(500.00), BigDecimal.ZERO, BigDecimal.ZERO);

        promotionService.applyCouponToBill(adminA, bill.getId(), "NODUP");
        assertThatThrownBy(() -> promotionService.applyCouponToBill(adminA, bill.getId(), "NODUP"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already applied");

        // Remove coupon -> usage returns to 0 and grandTotal returns to 500.00
        CouponApplicationResponse removed = promotionService.removeCouponFromBill(adminA, bill.getId(), "NODUP");
        assertThat(removed.discountAmount()).isEqualByComparingTo("0.00");
        assertThat(removed.grandTotal()).isEqualByComparingTo("500.00");
        assertThat(promotionRepository.findById(promo.id()).orElseThrow().getCurrentUsageCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("13. Discount + Payment + Refund calculation respects discounted amount and 14. Decimal precision")
    void testDiscountRefundAndDecimalPrecision() {
        // 15.55% off 333.33 = 51.832815 -> rounds HALF_UP to 51.83
        promotionService.createPromotion(adminA, basePromo("PRECISE15", DiscountType.PERCENTAGE, new BigDecimal("15.55")));
        RestaurantBill bill = createBill(tenantA.getId(), branchA1.getId(), customerSessionId, new BigDecimal("333.33"), BigDecimal.ZERO, BigDecimal.ZERO);

        CouponApplicationResponse applied = promotionService.applyCouponToBill(adminA, bill.getId(), "PRECISE15");
        assertThat(applied.discountAmount()).isEqualByComparingTo("51.83");
        assertThat(applied.grandTotal()).isEqualByComparingTo("281.50");

        // Customer pays discounted grandTotal (281.50)
        PaymentResponse payment = paymentService.recordStaffCashPayment(adminA, new RecordCashPaymentRequest(
                bill.getId(),
                new BigDecimal("281.50"),
                "Settled post-discount"
        ));

        // Attempting to refund pre-discount subtotal (333.33) must fail
        assertThatThrownBy(() -> refundService.requestRefund(adminA, new RequestRefundRequest(
                payment.id(),
                new BigDecimal("333.33"),
                RefundMethod.CASH,
                "Attempting over-refund"
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("exceeds remaining refundable balance");

        // Full refund of actual paid post-discount amount (281.50) succeeds
        RefundResponse refund = refundService.requestRefund(adminA, new RequestRefundRequest(
                payment.id(),
                new BigDecimal("281.50"),
                RefundMethod.CASH,
                "Full post-discount refund"
        ));
        RefundResponse executed = refundService.executeRefund(adminA, refund.id());
        assertThat(executed.refundAmount()).isEqualByComparingTo("281.50");
        assertThat(executed.refundStatus()).isEqualTo("SUCCEEDED");
    }
}
