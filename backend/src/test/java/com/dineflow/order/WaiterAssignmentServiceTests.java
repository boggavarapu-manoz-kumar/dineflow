package com.dineflow.order;

import com.dineflow.branch.domain.Branch;
import com.dineflow.branch.repository.BranchRepository;
import com.dineflow.common.exception.BusinessException;
import com.dineflow.identity.domain.MembershipStatus;
import com.dineflow.identity.domain.Role;
import com.dineflow.identity.domain.User;
import com.dineflow.identity.domain.UserMembership;
import com.dineflow.identity.domain.UserStatus;
import com.dineflow.identity.repository.RoleRepository;
import com.dineflow.identity.repository.UserMembershipRepository;
import com.dineflow.identity.repository.UserRepository;
import com.dineflow.order.domain.OrderStatus;
import com.dineflow.order.domain.RestaurantOrder;
import com.dineflow.order.dto.AssignWaiterRequest;
import com.dineflow.order.dto.OrderAssignmentResponse;
import com.dineflow.order.dto.OrderResponse;
import com.dineflow.order.repository.RestaurantOrderAssignmentRepository;
import com.dineflow.order.repository.RestaurantOrderRepository;
import com.dineflow.order.service.WaiterAssignmentService;
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
public class WaiterAssignmentServiceTests {

    @Autowired
    private WaiterAssignmentService assignmentService;

    @Autowired
    private RestaurantOrderRepository orderRepository;

    @Autowired
    private RestaurantOrderAssignmentRepository assignmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserMembershipRepository membershipRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private BranchRepository branchRepository;

    private Tenant tenant1;
    private Tenant tenant2;
    private Branch branch1;
    private Branch branch2;
    private Role waiterRole;
    private Role adminRole;
    private User waiter1;
    private User waiter2;
    private User manager;
    private RestaurantOrder order1;

    private DineFlowPrincipal managerPrincipal;
    private DineFlowPrincipal waiterPrincipal1;
    private DineFlowPrincipal waiterPrincipal2;

    @BeforeEach
    void setUp() {
        assignmentRepository.deleteAll();
        orderRepository.deleteAll();
        membershipRepository.deleteAll();
        userRepository.deleteAll();
        branchRepository.deleteAll();
        tenantRepository.deleteAll();

        // 1. Tenants
        tenant1 = new Tenant();
        tenant1.setSlug("waiter-tenant-1");
        tenant1.setBusinessName("Waiter Tenant 1");
        tenant1.setStatus("ACTIVE");
        tenant1.setSubscriptionPlan("STANDARD");
        tenant1 = tenantRepository.save(tenant1);

        tenant2 = new Tenant();
        tenant2.setSlug("waiter-tenant-2");
        tenant2.setBusinessName("Waiter Tenant 2");
        tenant2.setStatus("ACTIVE");
        tenant2.setSubscriptionPlan("STANDARD");
        tenant2 = tenantRepository.save(tenant2);

        // 2. Branches
        branch1 = new Branch("Downtown Branch", "DT01", "Downtown Ave", "111", true, true);
        branch1.setTenantId(tenant1.getId());
        branch1 = branchRepository.save(branch1);

        branch2 = new Branch("Uptown Branch", "UT02", "Uptown Blvd", "222", true, false);
        branch2.setTenantId(tenant1.getId());
        branch2 = branchRepository.save(branch2);

        // 3. Roles
        waiterRole = roleRepository.findByCode("WAITER")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setCode("WAITER");
                    r.setName("Waitstaff");
                    return roleRepository.save(r);
                });

        adminRole = roleRepository.findByCode("HOTEL_ADMIN")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setCode("HOTEL_ADMIN");
                    r.setName("Hotel Administrator");
                    return roleRepository.save(r);
                });

        // 4. Users
        waiter1 = new User();
        waiter1.setEmail("waiter1@bistro.com");
        waiter1.setFullName("John Waiter");
        waiter1.setStatus(UserStatus.ACTIVE);
        waiter1 = userRepository.save(waiter1);

        UserMembership mem1 = new UserMembership();
        mem1.setUser(waiter1);
        mem1.setTenantId(tenant1.getId());
        mem1.setRole(waiterRole);
        mem1.setStatus(MembershipStatus.ACTIVE);
        membershipRepository.save(mem1);

        waiter2 = new User();
        waiter2.setEmail("waiter2@bistro.com");
        waiter2.setFullName("Alice Waiter");
        waiter2.setStatus(UserStatus.ACTIVE);
        waiter2 = userRepository.save(waiter2);

        UserMembership mem2 = new UserMembership();
        mem2.setUser(waiter2);
        mem2.setTenantId(tenant1.getId());
        mem2.setRole(waiterRole);
        mem2.setStatus(MembershipStatus.ACTIVE);
        membershipRepository.save(mem2);

        manager = new User();
        manager.setEmail("manager@bistro.com");
        manager.setFullName("Bob Manager");
        manager.setStatus(UserStatus.ACTIVE);
        manager = userRepository.save(manager);

        UserMembership memMgr = new UserMembership();
        memMgr.setUser(manager);
        memMgr.setTenantId(tenant1.getId());
        memMgr.setRole(adminRole);
        memMgr.setStatus(MembershipStatus.ACTIVE);
        membershipRepository.save(memMgr);

        // 5. Order
        order1 = new RestaurantOrder(tenant1.getId(), branch1.getId(), UUID.randomUUID(), "T-05", "ORD-20261121-WT01");
        order1.setSubtotal(new BigDecimal("50.00"));
        order1.setGrandTotal(new BigDecimal("57.50"));
        order1.setStatus(OrderStatus.PLACED);
        order1 = orderRepository.save(order1);

        // 6. Principals
        managerPrincipal = new DineFlowPrincipal(
                manager.getId(), tenant1.getId(), branch1.getId(), "HOTEL_ADMIN",
                List.of("ORDER_VIEW", "ORDER_MANAGE", "WAITER_ASSIGN"), "STAFF"
        );

        waiterPrincipal1 = new DineFlowPrincipal(
                waiter1.getId(), tenant1.getId(), branch1.getId(), "WAITER",
                List.of("ORDER_VIEW", "WAITER_VIEW", "ORDER_ACCEPT", "ORDER_SERVE"), "STAFF"
        );

        waiterPrincipal2 = new DineFlowPrincipal(
                waiter2.getId(), tenant1.getId(), branch1.getId(), "WAITER",
                List.of("ORDER_VIEW", "WAITER_VIEW", "ORDER_ACCEPT", "ORDER_SERVE"), "STAFF"
        );
    }

    @Test
    @DisplayName("Valid Assignment: Manager assigns order to Waiter 1")
    void assignOrderToWaiter_success() {
        OrderResponse response = assignmentService.assignOrderToWaiter(
                managerPrincipal, order1.getId(), new AssignWaiterRequest(waiter1.getId(), "Assigned table 5")
        );

        assertThat(response).isNotNull();
        assertThat(response.assignedWaiterId()).isEqualTo(waiter1.getId());

        // Waiter 1 can now see it in their assigned orders queue
        List<OrderResponse> waiter1Orders = assignmentService.getOrdersAssignedToWaiter(waiterPrincipal1);
        assertThat(waiter1Orders).hasSize(1);
        assertThat(waiter1Orders.get(0).orderId()).isEqualTo(order1.getId());

        // Waiter 2 cannot see it in their assigned orders
        List<OrderResponse> waiter2Orders = assignmentService.getOrdersAssignedToWaiter(waiterPrincipal2);
        assertThat(waiter2Orders).isEmpty();

        // Verify assignment history has active record
        List<OrderAssignmentResponse> history = assignmentService.getAssignmentHistory(managerPrincipal, order1.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).waiterId()).isEqualTo(waiter1.getId());
        assertThat(history.get(0).assignmentStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Reassignment: Manager reassigns order from Waiter 1 to Waiter 2, archiving previous assignment")
    void reassignOrder_archivesPreviousAssignment() {
        assignmentService.assignOrderToWaiter(
                managerPrincipal, order1.getId(), new AssignWaiterRequest(waiter1.getId(), "First shift")
        );

        OrderResponse reassigned = assignmentService.assignOrderToWaiter(
                managerPrincipal, order1.getId(), new AssignWaiterRequest(waiter2.getId(), "Shift change")
        );

        assertThat(reassigned.assignedWaiterId()).isEqualTo(waiter2.getId());

        // Waiter 1 queue is now empty; Waiter 2 queue has the order
        assertThat(assignmentService.getOrdersAssignedToWaiter(waiterPrincipal1)).isEmpty();
        assertThat(assignmentService.getOrdersAssignedToWaiter(waiterPrincipal2)).hasSize(1);

        // Verify assignment history preserved both records
        List<OrderAssignmentResponse> history = assignmentService.getAssignmentHistory(managerPrincipal, order1.getId());
        assertThat(history).hasSize(2);
        assertThat(history.get(0).waiterId()).isEqualTo(waiter2.getId());
        assertThat(history.get(0).assignmentStatus()).isEqualTo("ACTIVE");
        assertThat(history.get(1).waiterId()).isEqualTo(waiter1.getId());
        assertThat(history.get(1).assignmentStatus()).isEqualTo("REASSIGNED");
        assertThat(history.get(1).unassignedAt()).isNotNull();
    }

    @Test
    @DisplayName("Unassignment: Manager unassigns order; order becomes unassigned and history recorded")
    void unassignOrder_removesWaiterAssignment() {
        assignmentService.assignOrderToWaiter(
                managerPrincipal, order1.getId(), new AssignWaiterRequest(waiter1.getId(), "Assigned")
        );

        OrderResponse unassigned = assignmentService.unassignOrder(managerPrincipal, order1.getId(), "Staff took break");
        assertThat(unassigned.assignedWaiterId()).isNull();

        assertThat(assignmentService.getOrdersAssignedToWaiter(waiterPrincipal1)).isEmpty();

        List<OrderAssignmentResponse> history = assignmentService.getAssignmentHistory(managerPrincipal, order1.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).assignmentStatus()).isEqualTo("UNASSIGNED");
        assertThat(history.get(0).reason()).isEqualTo("Staff took break");
    }

    @Test
    @DisplayName("Cross-tenant assignment rejection: Cannot assign order to a staff member of another tenant")
    void crossTenantAssignment_rejected() {
        User otherTenantUser = new User();
        otherTenantUser.setEmail("other@tenant2.com");
        otherTenantUser.setFullName("Foreign Staff");
        otherTenantUser.setStatus(UserStatus.ACTIVE);
        otherTenantUser = userRepository.save(otherTenantUser);

        UserMembership foreignMem = new UserMembership();
        foreignMem.setUser(otherTenantUser);
        foreignMem.setTenantId(tenant2.getId());
        foreignMem.setRole(waiterRole);
        foreignMem.setStatus(MembershipStatus.ACTIVE);
        membershipRepository.save(foreignMem);

        final UUID foreignStaffId = otherTenantUser.getId();
        assertThatThrownBy(() -> assignmentService.assignOrderToWaiter(
                managerPrincipal, order1.getId(), new AssignWaiterRequest(foreignStaffId, "Try illegal assign")
        )).isInstanceOf(BusinessException.class)
          .hasMessageContaining("does not belong to this hotel/tenant");
    }

    @Test
    @DisplayName("Inactive waiter rejection: Cannot assign order to an inactive or disabled user")
    void inactiveWaiter_rejected() {
        waiter1.setStatus(UserStatus.DISABLED);
        userRepository.save(waiter1);

        assertThatThrownBy(() -> assignmentService.assignOrderToWaiter(
                managerPrincipal, order1.getId(), new AssignWaiterRequest(waiter1.getId(), "Assigning suspended staff")
        )).isInstanceOf(BusinessException.class)
          .hasMessageContaining("inactive staff member");
    }

    @Test
    @DisplayName("Cross-branch assignment rejection: Manager from Branch 2 cannot assign Branch 1's order")
    void crossBranchManager_rejected() {
        DineFlowPrincipal branch2Manager = new DineFlowPrincipal(
                manager.getId(), tenant1.getId(), branch2.getId(), "HOTEL_ADMIN",
                List.of("WAITER_ASSIGN"), "STAFF"
        );

        assertThatThrownBy(() -> assignmentService.assignOrderToWaiter(
                branch2Manager, order1.getId(), new AssignWaiterRequest(waiter1.getId(), "Cross-branch assign")
        )).isInstanceOf(BusinessException.class)
          .hasMessageContaining("belongs to another branch");
    }
}
