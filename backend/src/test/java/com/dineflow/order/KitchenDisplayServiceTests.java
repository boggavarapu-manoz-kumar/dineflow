package com.dineflow.order;

import com.dineflow.branch.domain.Branch;
import com.dineflow.branch.repository.BranchRepository;
import com.dineflow.common.exception.BusinessException;
import com.dineflow.order.domain.OrderStatus;
import com.dineflow.order.domain.RestaurantOrder;
import com.dineflow.order.domain.RestaurantOrderItem;
import com.dineflow.order.dto.KitchenOrderResponse;
import com.dineflow.order.dto.OrderResponse;
import com.dineflow.order.repository.RestaurantOrderRepository;
import com.dineflow.order.service.KitchenDisplayService;
import com.dineflow.security.DineFlowPrincipal;
import com.dineflow.tenant.domain.Tenant;
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
public class KitchenDisplayServiceTests {

    @Autowired
    private KitchenDisplayService kitchenService;

    @Autowired
    private RestaurantOrderRepository orderRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private BranchRepository branchRepository;

    private Tenant tenant1;
    private Tenant tenant2;
    private Branch branch1;
    private Branch branch2;

    private RestaurantOrder orderPlaced;
    private RestaurantOrder orderAccepted;
    private RestaurantOrder orderPreparing;
    private RestaurantOrder orderReady;
    private RestaurantOrder orderServed;

    private DineFlowPrincipal kitchenPrincipalBranch1;
    private DineFlowPrincipal kitchenPrincipalBranch2;
    private DineFlowPrincipal crossTenantKitchenPrincipal;
    private DineFlowPrincipal waiterPrincipal;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        branchRepository.deleteAll();
        tenantRepository.deleteAll();

        tenant1 = new Tenant();
        tenant1.setSlug("kds-tenant-1");
        tenant1.setBusinessName("KDS Tenant 1");
        tenant1.setStatus("ACTIVE");
        tenant1.setSubscriptionPlan("STANDARD");
        tenant1 = tenantRepository.save(tenant1);

        tenant2 = new Tenant();
        tenant2.setSlug("kds-tenant-2");
        tenant2.setBusinessName("KDS Tenant 2");
        tenant2.setStatus("ACTIVE");
        tenant2.setSubscriptionPlan("STANDARD");
        tenant2 = tenantRepository.save(tenant2);

        branch1 = new Branch("Main Kitchen", "MK01", "100 Cook St", "111", true, true);
        branch1.setTenantId(tenant1.getId());
        branch1 = branchRepository.save(branch1);

        branch2 = new Branch("Secondary Kitchen", "SK02", "200 Cook St", "222", true, false);
        branch2.setTenantId(tenant1.getId());
        branch2 = branchRepository.save(branch2);

        // 1. Placed order (NOT in kitchen queue yet)
        orderPlaced = createTestOrder(tenant1.getId(), branch1.getId(), "T-01", "ORD-20261121-KD01", OrderStatus.PLACED);

        // 2. Accepted order (IN queue, ready to prepare)
        orderAccepted = createTestOrder(tenant1.getId(), branch1.getId(), "T-02", "ORD-20261121-KD02", OrderStatus.ACCEPTED);

        // 3. Preparing order (IN queue, cooking)
        orderPreparing = createTestOrder(tenant1.getId(), branch1.getId(), "T-03", "ORD-20261121-KD03", OrderStatus.PREPARING);

        // 4. Ready order (IN queue, awaiting waitstaff pickup)
        orderReady = createTestOrder(tenant1.getId(), branch1.getId(), "T-04", "ORD-20261121-KD04", OrderStatus.READY);

        // 5. Served order (NOT in kitchen queue)
        orderServed = createTestOrder(tenant1.getId(), branch1.getId(), "T-05", "ORD-20261121-KD05", OrderStatus.SERVED);

        // Principals
        kitchenPrincipalBranch1 = new DineFlowPrincipal(
                UUID.randomUUID(), tenant1.getId(), branch1.getId(), "KITCHEN_STAFF",
                List.of("KITCHEN_VIEW", "KITCHEN_UPDATE"), "STAFF"
        );

        kitchenPrincipalBranch2 = new DineFlowPrincipal(
                UUID.randomUUID(), tenant1.getId(), branch2.getId(), "KITCHEN_STAFF",
                List.of("KITCHEN_VIEW", "KITCHEN_UPDATE"), "STAFF"
        );

        crossTenantKitchenPrincipal = new DineFlowPrincipal(
                UUID.randomUUID(), tenant2.getId(), null, "KITCHEN_STAFF",
                List.of("KITCHEN_VIEW", "KITCHEN_UPDATE"), "STAFF"
        );

        waiterPrincipal = new DineFlowPrincipal(
                UUID.randomUUID(), tenant1.getId(), branch1.getId(), "WAITER",
                List.of("WAITER_VIEW", "ORDER_VIEW", "ORDER_ACCEPT", "ORDER_SERVE"), "STAFF"
        );
    }

    private RestaurantOrder createTestOrder(UUID tenantId, UUID branchId, String tableNum, String orderNum, OrderStatus status) {
        RestaurantOrder o = new RestaurantOrder(tenantId, branchId, UUID.randomUUID(), tableNum, orderNum);
        o.setStatus(status);
        o.setSubtotal(new BigDecimal("80.00"));
        o.setGrandTotal(new BigDecimal("92.00"));
        o.setTotalItemCount(2);

        RestaurantOrderItem item = new RestaurantOrderItem(
                o, UUID.randomUUID(), null, "Truffle Fries", null, "Crispy fries",
                2, new BigDecimal("40.00"), BigDecimal.ZERO, new BigDecimal("40.00"), new BigDecimal("80.00"), "Extra salt"
        );
        o.addItem(item);
        return orderRepository.save(o);
    }

    @Test
    @Transactional
    @DisplayName("Kitchen Queue Filtering: Only ACCEPTED, PREPARING, and READY orders are displayed in queue")
    void getKitchenQueue_returnsOnlyActiveKitchenOrders() {
        List<KitchenOrderResponse> queue = kitchenService.getKitchenQueue(kitchenPrincipalBranch1);

        assertThat(queue).hasSize(3);
        List<String> orderNumbers = queue.stream().map(KitchenOrderResponse::orderNumber).toList();
        assertThat(orderNumbers).containsExactly("ORD-20261121-KD02", "ORD-20261121-KD03", "ORD-20261121-KD04");

        // Verify snapshots are populated
        KitchenOrderResponse first = queue.get(0);
        assertThat(first.tableNumber()).isEqualTo("T-02");
        assertThat(first.items()).hasSize(1);
        assertThat(first.items().get(0).itemName()).isEqualTo("Truffle Fries");
        assertThat(first.items().get(0).specialInstructions()).isEqualTo("Extra salt");
    }

    @Test
    @DisplayName("Branch Isolation: Branch 2 kitchen does not see Branch 1 kitchen queue")
    void getKitchenQueue_branchIsolation() {
        List<KitchenOrderResponse> queue = kitchenService.getKitchenQueue(kitchenPrincipalBranch2);
        assertThat(queue).isEmpty();
    }

    @Test
    @DisplayName("Tenant Isolation: Tenant 2 kitchen does not see Tenant 1 kitchen queue")
    void getKitchenQueue_tenantIsolation() {
        List<KitchenOrderResponse> queue = kitchenService.getKitchenQueue(crossTenantKitchenPrincipal);
        assertThat(queue).isEmpty();
    }

    @Test
    @DisplayName("Preparation Transition: Kitchen starts cooking (ACCEPTED -> PREPARING)")
    void startPreparation_updatesToPreparing() {
        OrderResponse response = kitchenService.startPreparation(kitchenPrincipalBranch1, orderAccepted.getId(), "Cooking batch 1");
        assertThat(response.status()).isEqualTo(OrderStatus.PREPARING);

        RestaurantOrder updated = orderRepository.findById(orderAccepted.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.PREPARING);
    }

    @Test
    @DisplayName("Ready Transition: Kitchen completes cooking (PREPARING -> READY)")
    void markReady_updatesToReady() {
        OrderResponse response = kitchenService.markReady(kitchenPrincipalBranch1, orderPreparing.getId(), "Plated and warm");
        assertThat(response.status()).isEqualTo(OrderStatus.READY);

        RestaurantOrder updated = orderRepository.findById(orderPreparing.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.READY);
    }

    @Test
    @DisplayName("Permission Enforcement: Staff without KITCHEN_UPDATE cannot mark order ready")
    void markReady_unauthorizedStaff_throwsBusinessException() {
        assertThatThrownBy(() -> kitchenService.markReady(waiterPrincipal, orderPreparing.getId(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not have permission");
    }

    @Test
    @DisplayName("Invalid State Guard: Kitchen cannot jump straight from PLACED to READY without being ACCEPTED")
    void markReady_invalidState_throwsBusinessException() {
        assertThatThrownBy(() -> kitchenService.markReady(kitchenPrincipalBranch1, orderPlaced.getId(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Illegal status transition");
    }
}
