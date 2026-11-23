package com.dineflow.billing;

import com.dineflow.billing.domain.BillStatus;
import com.dineflow.billing.dto.BillResponse;
import com.dineflow.billing.dto.GenerateBillRequest;
import com.dineflow.billing.dto.VoidBillRequest;
import com.dineflow.billing.repository.RestaurantBillRepository;
import com.dineflow.billing.service.BillingEngineService;
import com.dineflow.branch.domain.Branch;
import com.dineflow.branch.repository.BranchRepository;
import com.dineflow.common.exception.BusinessException;
import com.dineflow.order.domain.OrderStatus;
import com.dineflow.order.domain.RestaurantOrder;
import com.dineflow.order.domain.RestaurantOrderItem;
import com.dineflow.order.domain.RestaurantOrderItemOption;
import com.dineflow.order.repository.RestaurantOrderRepository;
import com.dineflow.security.DineFlowPrincipal;
import com.dineflow.tenant.domain.HotelProfile;
import com.dineflow.tenant.domain.Tenant;
import com.dineflow.tenant.repository.HotelProfileRepository;
import com.dineflow.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class BillingEngineServiceTests {

    @Autowired
    private BillingEngineService billingService;

    @Autowired
    private RestaurantBillRepository billRepository;

    @Autowired
    private RestaurantOrderRepository orderRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private HotelProfileRepository hotelProfileRepository;

    private UUID tenantId;
    private UUID branchId;
    private UUID sessionId;
    private UUID staffId;
    private DineFlowPrincipal staffPrincipal;
    private RestaurantOrder order1;
    private RestaurantOrder order2;

    @BeforeEach
    void setUp() {
        billRepository.deleteAll();
        orderRepository.deleteAll();
        hotelProfileRepository.deleteAll();
        branchRepository.deleteAll();
        tenantRepository.deleteAll();

        // 1. Seed Tenant
        Tenant tenant = new Tenant();
        tenant.setSlug("taj-palace-" + UUID.randomUUID());
        tenant.setBusinessName("Taj Palace Luxury Hotel");
        tenant.setStatus("ACTIVE");
        tenant.setSubscriptionPlan("ENTERPRISE");
        tenant = tenantRepository.save(tenant);
        tenantId = tenant.getId();

        sessionId = UUID.randomUUID();
        staffId = UUID.randomUUID();

        // 2. Seed Branch
        Branch branch = new Branch("Main Dining Pavilion", "MAIN", "Level 1, Taj Palace", "+91 9999988888", true, true);
        branch.setTenantId(tenantId);
        branch = branchRepository.save(branch);
        branchId = branch.getId();

        // 3. Seed Hotel Profile (10% Tax, 5% Service Charge)
        HotelProfile profile = new HotelProfile();
        profile.setTenantId(tenantId);
        profile.setCurrencyCode("INR");
        profile.setTaxPercentage(new BigDecimal("10.00"));
        profile.setServiceChargePercentage(new BigDecimal("5.00"));
        hotelProfileRepository.save(profile);

        // 4. Seed Restaurant Order 1 (Subtotal: 100.00 + 30.00 options = 130.00)
        order1 = new RestaurantOrder(tenantId, branchId, sessionId, "T-01", "ORD-20261122-0001");
        order1.setStatus(OrderStatus.SERVED);
        order1.setCurrencyCode("INR");
        order1.setSubtotal(new BigDecimal("130.00"));
        order1.setTaxPercentage(new BigDecimal("10.00"));
        order1.setTaxAmount(new BigDecimal("13.00"));
        order1.setServiceChargePercentage(new BigDecimal("5.00"));
        order1.setServiceChargeAmount(new BigDecimal("6.50"));
        order1.setGrandTotal(new BigDecimal("149.50"));
        order1.setTotalItemCount(1);

        RestaurantOrderItem item1 = new RestaurantOrderItem(
                order1, UUID.randomUUID(), UUID.randomUUID(), "Paneer Tikka", "Regular",
                "Spiced grilled cottage cheese", 1, new BigDecimal("100.00"), new BigDecimal("30.00"),
                new BigDecimal("130.00"), new BigDecimal("130.00"), "Extra mint chutney"
        );
        RestaurantOrderItemOption opt1 = new RestaurantOrderItemOption(
                item1, UUID.randomUUID(), "Extra Truffle Glaze", new BigDecimal("30.00")
        );
        item1.addOption(opt1);
        order1.addItem(item1);
        order1 = orderRepository.save(order1);

        // 5. Seed Restaurant Order 2 (Subtotal: 200.00)
        order2 = new RestaurantOrder(tenantId, branchId, sessionId, "T-01", "ORD-20261122-0002");
        order2.setStatus(OrderStatus.PLACED);
        order2.setCurrencyCode("INR");
        order2.setSubtotal(new BigDecimal("200.00"));
        order2.setTaxPercentage(new BigDecimal("10.00"));
        order2.setTaxAmount(new BigDecimal("20.00"));
        order2.setServiceChargePercentage(new BigDecimal("5.00"));
        order2.setServiceChargeAmount(new BigDecimal("10.00"));
        order2.setGrandTotal(new BigDecimal("230.00"));
        order2.setTotalItemCount(1);

        RestaurantOrderItem item2 = new RestaurantOrderItem(
                order2, UUID.randomUUID(), null, "Biryani Deluxe", null,
                "Royal aromatic rice", 1, new BigDecimal("200.00"), BigDecimal.ZERO,
                new BigDecimal("200.00"), new BigDecimal("200.00"), null
        );
        order2.addItem(item2);
        order2 = orderRepository.save(order2);

        staffPrincipal = new DineFlowPrincipal(staffId, tenantId, branchId, "HOTEL_ADMIN", List.of("BILL_CREATE", "BILL_VIEW", "BILL_VOID"), "STAFF");
    }

    @Test
    @DisplayName("Should accurately generate bill from confirmed order with tax, service charge, and snapshots")
    void shouldGenerateBillFromOrderAccurately() {
        GenerateBillRequest request = new GenerateBillRequest(List.of(order1.getId()), null, "Customer requested bill");
        BillResponse bill = billingService.generateBillForOrders(staffPrincipal, request);

        assertThat(bill).isNotNull();
        assertThat(bill.billNumber()).startsWith("BILL-");
        assertThat(bill.tableNumber()).isEqualTo("T-01");
        assertThat(bill.restaurantName()).isEqualTo("Taj Palace Luxury Hotel");
        assertThat(bill.branchName()).isEqualTo("Main Dining Pavilion");
        assertThat(bill.branchAddress()).isEqualTo("Level 1, Taj Palace");
        assertThat(bill.status()).isEqualTo("ISSUED");
        assertThat(bill.currencyCode()).isEqualTo("INR");

        // Subtotal = 130.00
        assertThat(bill.subtotal()).isEqualByComparingTo("130.00");
        assertThat(bill.discountAmount()).isEqualByComparingTo("0.00");

        // Service charge 5% of 130.00 = 6.50
        assertThat(bill.serviceChargePercentage()).isEqualByComparingTo("5.00");
        assertThat(bill.serviceChargeAmount()).isEqualByComparingTo("6.50");

        // Taxable base = 130.00 + 6.50 = 136.50
        // Tax 10% of 136.50 = 13.65
        assertThat(bill.taxPercentage()).isEqualByComparingTo("10.00");
        assertThat(bill.taxAmount()).isEqualByComparingTo("13.65");

        // Grand total = 136.50 + 13.65 = 150.15
        assertThat(bill.grandTotal()).isEqualByComparingTo("150.15");
        assertThat(bill.paidAmount()).isEqualByComparingTo("0.00");
        assertThat(bill.outstandingBalance()).isEqualByComparingTo("150.15");

        // Snapshots verify
        assertThat(bill.items()).hasSize(1);
        assertThat(bill.items().get(0).itemName()).isEqualTo("Paneer Tikka");
        assertThat(bill.items().get(0).options()).hasSize(1);
        assertThat(bill.items().get(0).options().get(0).optionName()).isEqualTo("Extra Truffle Glaze");
    }

    @Test
    @DisplayName("Should aggregate multiple table orders into a single consolidated bill")
    void shouldAggregateMultipleOrdersIntoSingleBill() {
        GenerateBillRequest request = new GenerateBillRequest(List.of(order1.getId(), order2.getId()), null, "Consolidated bill");
        BillResponse bill = billingService.generateBillForOrders(staffPrincipal, request);

        assertThat(bill).isNotNull();
        // Subtotal = 130.00 + 200.00 = 330.00
        assertThat(bill.subtotal()).isEqualByComparingTo("330.00");

        // Service charge 5% of 330 = 16.50
        assertThat(bill.serviceChargeAmount()).isEqualByComparingTo("16.50");

        // Taxable = 330 + 16.50 = 346.50
        // Tax 10% of 346.50 = 34.65
        assertThat(bill.taxAmount()).isEqualByComparingTo("34.65");

        // Grand total = 346.50 + 34.65 = 381.15
        assertThat(bill.grandTotal()).isEqualByComparingTo("381.15");
        assertThat(bill.items()).hasSize(2);
        assertThat(bill.orderNumbers()).containsExactlyInAnyOrder("ORD-20261122-0001", "ORD-20261122-0002");
    }

    @Test
    @DisplayName("Should apply discretionary discount and recompute tax and service charge accurately")
    void shouldApplyDiscountCorrectly() {
        // Subtotal 130.00 - 30.00 discount = 100.00 net taxable
        GenerateBillRequest request = new GenerateBillRequest(List.of(order1.getId()), new BigDecimal("30.00"), "Loyalty discount");
        BillResponse bill = billingService.generateBillForOrders(staffPrincipal, request);

        assertThat(bill.subtotal()).isEqualByComparingTo("130.00");
        assertThat(bill.discountAmount()).isEqualByComparingTo("30.00");

        // Net = 100.00
        // Service charge 5% of 100.00 = 5.00
        assertThat(bill.serviceChargeAmount()).isEqualByComparingTo("5.00");

        // Taxable base = 100.00 + 5.00 = 105.00
        // Tax 10% of 105.00 = 10.50
        assertThat(bill.taxAmount()).isEqualByComparingTo("10.50");

        // Grand total = 105.00 + 10.50 = 115.50
        assertThat(bill.grandTotal()).isEqualByComparingTo("115.50");
        assertThat(bill.outstandingBalance()).isEqualByComparingTo("115.50");
    }

    @Test
    @DisplayName("Should prevent double billing of the same order")
    void shouldPreventDoubleBillingOfSameOrder() {
        GenerateBillRequest request = new GenerateBillRequest(List.of(order1.getId()), null, null);
        billingService.generateBillForOrders(staffPrincipal, request);

        // Attempting to bill order1 again must throw CONFLICT
        assertThatThrownBy(() -> billingService.generateBillForOrders(staffPrincipal, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already billed");
    }

    @Test
    @DisplayName("Should allow voiding an issued unpaid bill with mandatory reason")
    void shouldAllowVoidingIssuedBill() {
        GenerateBillRequest genReq = new GenerateBillRequest(List.of(order1.getId()), null, null);
        BillResponse bill = billingService.generateBillForOrders(staffPrincipal, genReq);

        VoidBillRequest voidReq = new VoidBillRequest("Customer requested split billing re-issue");
        BillResponse voidedBill = billingService.voidBill(staffPrincipal, bill.id(), voidReq);

        assertThat(voidedBill.status()).isEqualTo("VOIDED");
        assertThat(voidedBill.voidReason()).isEqualTo("Customer requested split billing re-issue");
        assertThat(voidedBill.voidedAt()).isNotNull();

        // Once voided, the order can now be re-billed under a new bill
        BillResponse newBill = billingService.generateBillForOrders(staffPrincipal, genReq);
        assertThat(newBill.id()).isNotEqualTo(bill.id());
        assertThat(newBill.status()).isEqualTo("ISSUED");
    }

    @Test
    @DisplayName("Should enforce cross-tenant and cross-branch access controls")
    void shouldEnforceTenantAndBranchIsolation() {
        UUID otherTenantId = UUID.randomUUID();
        DineFlowPrincipal foreignPrincipal = new DineFlowPrincipal(staffId, otherTenantId, branchId, "HOTEL_ADMIN", List.of("BILL_CREATE", "BILL_VIEW"), "STAFF");

        GenerateBillRequest request = new GenerateBillRequest(List.of(order1.getId()), null, null);
        assertThatThrownBy(() -> billingService.generateBillForOrders(foreignPrincipal, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unauthorized cross-tenant");
    }
}
