package com.dineflow.order;

import com.dineflow.branch.domain.Branch;
import com.dineflow.branch.repository.BranchRepository;
import com.dineflow.common.exception.BusinessException;
import com.dineflow.order.domain.OrderStatus;
import com.dineflow.order.domain.RestaurantOrder;
import com.dineflow.order.dto.CancelOrderRequest;
import com.dineflow.order.dto.OrderResponse;
import com.dineflow.order.dto.OrderStatusHistoryResponse;
import com.dineflow.order.dto.TransitionOrderStatusRequest;
import com.dineflow.order.repository.RestaurantOrderRepository;
import com.dineflow.order.service.OrderLifecycleService;
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
public class OrderLifecycleServiceTests {

    @Autowired
    private OrderLifecycleService lifecycleService;

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
    private UUID customerSessionId;
    private RestaurantOrder order;

    // Staff principals
    private DineFlowPrincipal waiterPrincipal;
    private DineFlowPrincipal kitchenPrincipal;
    private DineFlowPrincipal adminPrincipal;
    private DineFlowPrincipal otherBranchPrincipal;
    private DineFlowPrincipal crossTenantPrincipal;
    private DineFlowPrincipal customerPrincipal;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        branchRepository.deleteAll();
        tenantRepository.deleteAll();

        tenant1 = new Tenant();
        tenant1.setSlug("lifecycle-bistro");
        tenant1.setBusinessName("Lifecycle Bistro");
        tenant1.setStatus("ACTIVE");
        tenant1.setSubscriptionPlan("STANDARD");
        tenant1 = tenantRepository.save(tenant1);

        tenant2 = new Tenant();
        tenant2.setSlug("other-tenant");
        tenant2.setBusinessName("Other Tenant");
        tenant2.setStatus("ACTIVE");
        tenant2.setSubscriptionPlan("STANDARD");
        tenant2 = tenantRepository.save(tenant2);

        branch1 = new Branch("Main Branch", "MB1", "123 Street", "111", true, true);
        branch1.setTenantId(tenant1.getId());
        branch1 = branchRepository.save(branch1);

        branch2 = new Branch("North Branch", "NB2", "456 Street", "222", true, false);
        branch2.setTenantId(tenant1.getId());
        branch2 = branchRepository.save(branch2);

        customerSessionId = UUID.randomUUID();

        // Create base order in PLACED status
        order = new RestaurantOrder(tenant1.getId(), branch1.getId(), customerSessionId, "T-10", "ORD-20261121-0001");
        order.setSubtotal(new BigDecimal("100.00"));
        order.setGrandTotal(new BigDecimal("115.00"));
        order.setStatus(OrderStatus.PLACED);
        order = orderRepository.save(order);

        // Waiter: ORDER_VIEW, ORDER_ACCEPT, ORDER_SERVE
        waiterPrincipal = new DineFlowPrincipal(
                UUID.randomUUID(), tenant1.getId(), branch1.getId(), "WAITER",
                List.of("ORDER_VIEW", "ORDER_ACCEPT", "ORDER_SERVE"), "STAFF"
        );

        // Kitchen: KITCHEN_VIEW, KITCHEN_UPDATE
        kitchenPrincipal = new DineFlowPrincipal(
                UUID.randomUUID(), tenant1.getId(), branch1.getId(), "KITCHEN_STAFF",
                List.of("KITCHEN_VIEW", "KITCHEN_UPDATE"), "STAFF"
        );

        // Admin: ORDER_MANAGE, ORDER_CANCEL
        adminPrincipal = new DineFlowPrincipal(
                UUID.randomUUID(), tenant1.getId(), null, "HOTEL_ADMIN",
                List.of("ORDER_VIEW", "ORDER_MANAGE", "ORDER_CANCEL"), "STAFF"
        );

        // Other branch staff
        otherBranchPrincipal = new DineFlowPrincipal(
                UUID.randomUUID(), tenant1.getId(), branch2.getId(), "WAITER",
                List.of("ORDER_VIEW", "ORDER_ACCEPT"), "STAFF"
        );

        // Cross-tenant staff
        crossTenantPrincipal = new DineFlowPrincipal(
                UUID.randomUUID(), tenant2.getId(), null, "HOTEL_ADMIN",
                List.of("ORDER_VIEW", "ORDER_MANAGE"), "STAFF"
        );

        // Customer principal
        customerPrincipal = new DineFlowPrincipal(
                customerSessionId, tenant1.getId(), branch1.getId(), "CUSTOMER_GUEST",
                List.of("CUSTOMER_ORDER_VIEW_OWN"), "GUEST"
        );
    }

    @Test
    @DisplayName("Complete valid happy path lifecycle: PLACED -> ACCEPTED -> PREPARING -> READY -> SERVED -> PAID")
    void completeOrderLifecycle_happyPath() {
        // 1. Waiter accepts PLACED order
        OrderResponse accepted = lifecycleService.transitionOrderStatus(
                waiterPrincipal, order.getId(), new TransitionOrderStatusRequest(OrderStatus.ACCEPTED, "Order acknowledged")
        );
        assertThat(accepted.status()).isEqualTo(OrderStatus.ACCEPTED);

        // 2. Kitchen starts PREPARING
        OrderResponse preparing = lifecycleService.transitionOrderStatus(
                kitchenPrincipal, order.getId(), new TransitionOrderStatusRequest(OrderStatus.PREPARING, "Cooking on grill")
        );
        assertThat(preparing.status()).isEqualTo(OrderStatus.PREPARING);

        // 3. Kitchen marks READY
        OrderResponse ready = lifecycleService.transitionOrderStatus(
                kitchenPrincipal, order.getId(), new TransitionOrderStatusRequest(OrderStatus.READY, "Plated and garnished")
        );
        assertThat(ready.status()).isEqualTo(OrderStatus.READY);

        // 4. Waiter marks SERVED
        OrderResponse served = lifecycleService.transitionOrderStatus(
                waiterPrincipal, order.getId(), new TransitionOrderStatusRequest(OrderStatus.SERVED, "Delivered to table 10")
        );
        assertThat(served.status()).isEqualTo(OrderStatus.SERVED);

        // 5. Cashier / Admin marks PAID
        OrderResponse paid = lifecycleService.transitionOrderStatus(
                adminPrincipal, order.getId(), new TransitionOrderStatusRequest(OrderStatus.PAID, "Settled via card")
        );
        assertThat(paid.status()).isEqualTo(OrderStatus.PAID);

        // 6. Verify audit history records all transitions
        List<OrderStatusHistoryResponse> history = lifecycleService.getOrderStatusHistory(waiterPrincipal, order.getId());
        assertThat(history).hasSize(5);
        assertThat(history.get(0).newStatus()).isEqualTo("ACCEPTED");
        assertThat(history.get(1).newStatus()).isEqualTo("PREPARING");
        assertThat(history.get(2).newStatus()).isEqualTo("READY");
        assertThat(history.get(3).newStatus()).isEqualTo("SERVED");
        assertThat(history.get(4).newStatus()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("Illegal transition rejection: Cannot jump from PLACED directly to READY or SERVED")
    void illegalTransition_jumpRejection() {
        assertThatThrownBy(() -> lifecycleService.transitionOrderStatus(
                kitchenPrincipal, order.getId(), new TransitionOrderStatusRequest(OrderStatus.READY, null)
        )).isInstanceOf(BusinessException.class)
          .hasMessageContaining("Illegal status transition");
    }

    @Test
    @DisplayName("RBAC check: Waiter cannot transition to PREPARING (requires KITCHEN_UPDATE or ORDER_MANAGE)")
    void rbacCheck_waiterCannotStartPreparation() {
        order.setStatus(OrderStatus.ACCEPTED);
        orderRepository.save(order);

        assertThatThrownBy(() -> lifecycleService.transitionOrderStatus(
                waiterPrincipal, order.getId(), new TransitionOrderStatusRequest(OrderStatus.PREPARING, null)
        )).isInstanceOf(BusinessException.class)
          .hasMessageContaining("does not have permission");
    }

    @Test
    @DisplayName("Cross-tenant isolation: Staff from Tenant B cannot access or modify order from Tenant A")
    void crossTenantIsolation_rejected() {
        assertThatThrownBy(() -> lifecycleService.transitionOrderStatus(
                crossTenantPrincipal, order.getId(), new TransitionOrderStatusRequest(OrderStatus.ACCEPTED, null)
        )).isInstanceOf(BusinessException.class)
          .hasMessageContaining("Order not found");
    }

    @Test
    @DisplayName("Cross-branch isolation: Staff scoped to Branch 2 cannot transition order of Branch 1")
    void crossBranchIsolation_rejected() {
        assertThatThrownBy(() -> lifecycleService.transitionOrderStatus(
                otherBranchPrincipal, order.getId(), new TransitionOrderStatusRequest(OrderStatus.ACCEPTED, null)
        )).isInstanceOf(BusinessException.class)
          .hasMessageContaining("Order belongs to a different branch");
    }

    @Test
    @DisplayName("Customer cancellation: Allowed while order is in PLACED stage")
    void customerCancellation_allowedWhilePlaced() {
        OrderResponse cancelled = lifecycleService.cancelOrderAsCustomer(
                customerPrincipal, order.getId(), new CancelOrderRequest("Changed my mind")
        );
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);

        List<OrderStatusHistoryResponse> history = lifecycleService.getOrderStatusHistory(customerPrincipal, order.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).newStatus()).isEqualTo("CANCELLED");
        assertThat(history.get(0).actorType()).isEqualTo("GUEST");
    }

    @Test
    @DisplayName("Customer cancellation: Forbidden once kitchen has accepted or begun preparing")
    void customerCancellation_forbiddenOnceAccepted() {
        order.setStatus(OrderStatus.ACCEPTED);
        orderRepository.save(order);

        assertThatThrownBy(() -> lifecycleService.cancelOrderAsCustomer(
                customerPrincipal, order.getId(), new CancelOrderRequest("Want to cancel")
        )).isInstanceOf(BusinessException.class)
          .hasMessageContaining("kitchen has already accepted");
    }

    @Test
    @DisplayName("Staff cancellation requires a non-blank reason")
    void staffCancellation_requiresReason() {
        assertThatThrownBy(() -> lifecycleService.transitionOrderStatus(
                adminPrincipal, order.getId(), new TransitionOrderStatusRequest(OrderStatus.CANCELLED, "   ")
        )).isInstanceOf(BusinessException.class)
          .hasMessageContaining("cancellation reason is required");
    }

    @Test
    @DisplayName("Terminal state immutability: Cancelled or Paid order cannot be transitioned further")
    void terminalState_noFurtherTransitions() {
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        assertThatThrownBy(() -> lifecycleService.transitionOrderStatus(
                adminPrincipal, order.getId(), new TransitionOrderStatusRequest(OrderStatus.ACCEPTED, "Reopening")
        )).isInstanceOf(BusinessException.class)
          .hasMessageContaining("Illegal status transition");
    }
}
