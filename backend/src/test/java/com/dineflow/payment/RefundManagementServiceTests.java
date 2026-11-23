package com.dineflow.payment;

import com.dineflow.billing.domain.BillStatus;
import com.dineflow.billing.domain.RestaurantBill;
import com.dineflow.billing.repository.RestaurantBillRepository;
import com.dineflow.common.exception.BusinessException;
import com.dineflow.payment.domain.PaymentMethod;
import com.dineflow.payment.domain.PaymentProviderType;
import com.dineflow.payment.domain.PaymentStatus;
import com.dineflow.payment.domain.RefundMethod;
import com.dineflow.payment.domain.RefundStatus;
import com.dineflow.payment.domain.RestaurantPayment;
import com.dineflow.payment.dto.RecordCashPaymentRequest;
import com.dineflow.payment.dto.RefundResponse;
import com.dineflow.payment.dto.RequestRefundRequest;
import com.dineflow.payment.repository.RestaurantPaymentRepository;
import com.dineflow.payment.repository.RestaurantRefundRepository;
import com.dineflow.payment.service.PaymentProcessingService;
import com.dineflow.payment.service.RefundManagementService;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
public class RefundManagementServiceTests {

    @Autowired
    private RefundManagementService refundService;

    @Autowired
    private PaymentProcessingService paymentService;

    @Autowired
    private RestaurantRefundRepository refundRepository;

    @Autowired
    private RestaurantPaymentRepository paymentRepository;

    @Autowired
    private RestaurantBillRepository billRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private Tenant testTenant;
    private RestaurantBill testBill;
    private RestaurantPayment testPayment;
    private DineFlowPrincipal adminPrincipal;
    private DineFlowPrincipal staffPrincipal;

    @BeforeEach
    void setUp() {
        refundRepository.deleteAll();
        paymentRepository.deleteAll();
        billRepository.deleteAll();

        testTenant = tenantRepository.findAll().stream().findFirst().orElseGet(() -> {
            Tenant t = new Tenant();
            t.setBusinessName("Refund Test Hotel");
            t.setSlug("refund-test-" + UUID.randomUUID());
            t.setStatus("ACTIVE");
            t.setSubscriptionPlan("PRO");
            return tenantRepository.save(t);
        });

        testBill = new RestaurantBill();
        testBill.setTenantId(testTenant.getId());
        testBill.setBillNumber("BILL-20261122-REFUND");
        testBill.setTableNumber("T-02");
        testBill.setRestaurantNameSnapshot("Refund Test Hotel");
        testBill.setCurrencyCode("INR");
        testBill.setSubtotal(BigDecimal.valueOf(900.00));
        testBill.setTaxAmount(BigDecimal.valueOf(100.00));
        testBill.setGrandTotal(BigDecimal.valueOf(1000.00));
        testBill.setOutstandingBalance(BigDecimal.valueOf(1000.00));
        testBill.setPaidAmount(BigDecimal.ZERO);
        testBill.setStatus(BillStatus.ISSUED);
        testBill = billRepository.save(testBill);

        adminPrincipal = new DineFlowPrincipal(
                UUID.randomUUID(),
                testTenant.getId(),
                null,
                "HOTEL_ADMIN",
                List.of("REFUND_REQUEST", "REFUND_APPROVE", "REFUND_EXECUTE", "PAYMENT_PROCESS"),
                "STAFF"
        );

        staffPrincipal = new DineFlowPrincipal(
                UUID.randomUUID(),
                testTenant.getId(),
                null,
                "WAITER",
                List.of("REFUND_REQUEST"),
                "STAFF"
        );

        // Settle payment of 1000.00
        paymentService.recordStaffCashPayment(adminPrincipal, new RecordCashPaymentRequest(
                testBill.getId(),
                BigDecimal.valueOf(1000.00),
                "Initial full payment"
        ));
        testPayment = paymentRepository.findAllByBillIdAndTenantIdOrderByCreatedAtDesc(testBill.getId(), testTenant.getId()).get(0);
    }

    @Test
    @DisplayName("Partial cash refund requests and executes correctly with balance adjustments")
    void testPartialCashRefundExecution() {
        // 1. Request partial refund of 300.00
        RequestRefundRequest req = new RequestRefundRequest(
                testPayment.getId(),
                BigDecimal.valueOf(300.00),
                RefundMethod.CASH,
                "Disliked special dish"
        );

        RefundResponse requested = refundService.requestRefund(adminPrincipal, req);
        assertThat(requested.refundStatus()).isEqualTo(RefundStatus.APPROVED.name()); // Auto-approved under threshold

        // 2. Execute refund
        RefundResponse executed = refundService.executeRefund(adminPrincipal, requested.id());
        assertThat(executed.refundStatus()).isEqualTo(RefundStatus.SUCCEEDED.name());

        // 3. Verify Payment and Bill state
        RestaurantPayment updatedPayment = paymentRepository.findById(testPayment.getId()).orElseThrow();
        assertThat(updatedPayment.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(updatedPayment.getRefundedAmount()).isEqualByComparingTo(BigDecimal.valueOf(300.00));

        RestaurantBill updatedBill = billRepository.findById(testBill.getId()).orElseThrow();
        assertThat(updatedBill.getStatus()).isEqualTo(BillStatus.PARTIALLY_PAID);
        assertThat(updatedBill.getPaidAmount()).isEqualByComparingTo(BigDecimal.valueOf(700.00));
        assertThat(updatedBill.getOutstandingBalance()).isEqualByComparingTo(BigDecimal.valueOf(300.00));
    }

    @Test
    @DisplayName("Refund exceeding remaining paid balance is strictly rejected")
    void testRefundExceedingBalanceRejected() {
        RequestRefundRequest req = new RequestRefundRequest(
                testPayment.getId(),
                BigDecimal.valueOf(1200.00), // Exceeds 1000.00
                RefundMethod.CASH,
                "Excess request"
        );

        assertThatThrownBy(() -> refundService.requestRefund(adminPrincipal, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Requested refund");
    }

    @Test
    @DisplayName("Full refund marks payment as REFUNDED and restores bill to ISSUED")
    void testFullRefundExecution() {
        RequestRefundRequest req = new RequestRefundRequest(
                testPayment.getId(),
                BigDecimal.valueOf(1000.00),
                RefundMethod.CASH,
                "Order cancelled by manager"
        );

        RefundResponse requested = refundService.requestRefund(adminPrincipal, req);
        refundService.executeRefund(adminPrincipal, requested.id());

        RestaurantPayment updatedPayment = paymentRepository.findById(testPayment.getId()).orElseThrow();
        assertThat(updatedPayment.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(updatedPayment.getRefundedAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000.00));

        RestaurantBill updatedBill = billRepository.findById(testBill.getId()).orElseThrow();
        assertThat(updatedBill.getStatus()).isEqualTo(BillStatus.ISSUED);
        assertThat(updatedBill.getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(updatedBill.getOutstandingBalance()).isEqualByComparingTo(BigDecimal.valueOf(1000.00));
    }
}
