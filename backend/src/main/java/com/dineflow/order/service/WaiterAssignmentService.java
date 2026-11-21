package com.dineflow.order.service;

import com.dineflow.common.exception.BusinessException;
import com.dineflow.identity.domain.MembershipStatus;
import com.dineflow.identity.domain.User;
import com.dineflow.identity.domain.UserMembership;
import com.dineflow.identity.domain.UserStatus;
import com.dineflow.identity.repository.UserMembershipRepository;
import com.dineflow.identity.repository.UserRepository;
import com.dineflow.order.domain.RestaurantOrder;
import com.dineflow.order.domain.RestaurantOrderAssignment;
import com.dineflow.order.dto.AssignWaiterRequest;
import com.dineflow.order.dto.OrderAssignmentResponse;
import com.dineflow.order.dto.OrderResponse;
import com.dineflow.order.repository.RestaurantOrderAssignmentRepository;
import com.dineflow.order.repository.RestaurantOrderRepository;
import com.dineflow.security.DineFlowPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class WaiterAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(WaiterAssignmentService.class);

    private final RestaurantOrderRepository orderRepository;
    private final RestaurantOrderAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final UserMembershipRepository membershipRepository;
    private final OrderService orderService;

    public WaiterAssignmentService(RestaurantOrderRepository orderRepository,
                                   RestaurantOrderAssignmentRepository assignmentRepository,
                                   UserRepository userRepository,
                                   UserMembershipRepository membershipRepository,
                                   OrderService orderService) {
        this.orderRepository = orderRepository;
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.orderService = orderService;
    }

    /**
     * Assigns or reassigns an order to an authorized waiter.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderResponse assignOrderToWaiter(DineFlowPrincipal principal, UUID orderId, AssignWaiterRequest request) {
        UUID tenantId = principal.getTenantId();
        UUID assigningUserId = principal.getUserId();

        // 1. Load and validate order within tenant and branch scope
        RestaurantOrder order = orderRepository.findByIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> new BusinessException("Order not found", HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND"));

        if (principal.getBranchId() != null && order.getBranchId() != null && !order.getBranchId().equals(principal.getBranchId())) {
            throw new BusinessException("Access denied: Order belongs to another branch", HttpStatus.FORBIDDEN, "CROSS_BRANCH_ASSIGNMENT_DENIED");
        }

        // 2. Validate waiter user and tenant membership
        User waiter = userRepository.findById(request.waiterId())
                .orElseThrow(() -> new BusinessException("Waiter not found", HttpStatus.NOT_FOUND, "WAITER_NOT_FOUND"));

        if (waiter.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("Cannot assign order to an inactive staff member", HttpStatus.BAD_REQUEST, "STAFF_INACTIVE");
        }

        UserMembership membership = membershipRepository.findByUserIdAndTenantId(waiter.getId(), tenantId)
                .orElseThrow(() -> new BusinessException("Staff member does not belong to this hotel/tenant", HttpStatus.FORBIDDEN, "CROSS_TENANT_WAITER"));

        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new BusinessException("Staff tenant membership is suspended or inactive", HttpStatus.FORBIDDEN, "MEMBERSHIP_INACTIVE");
        }

        // Role verification: Must have WAITER role or WAITER_VIEW authority
        boolean isEligibleWaiter = "WAITER".equals(membership.getRole().getCode()) ||
                membership.getRole().getPermissions().stream().anyMatch(p -> "WAITER_VIEW".equals(p.getCode()));
        if (!isEligibleWaiter) {
            throw new BusinessException("Selected staff member is not an authorized waiter", HttpStatus.BAD_REQUEST, "INVALID_WAITER_ROLE");
        }

        // 3. Mark any existing active assignment as REASSIGNED
        Optional<RestaurantOrderAssignment> existingActive = assignmentRepository.findByOrderIdAndAssignmentStatus(orderId, "ACTIVE");
        if (existingActive.isPresent()) {
            RestaurantOrderAssignment prev = existingActive.get();
            prev.setAssignmentStatus("REASSIGNED");
            prev.setUnassignedAt(Instant.now());
            assignmentRepository.save(prev);
        }

        // 4. Create new active assignment record
        RestaurantOrderAssignment newAssignment = new RestaurantOrderAssignment(
                order,
                tenantId,
                order.getBranchId(),
                waiter.getId(),
                assigningUserId,
                request.reason() != null && !request.reason().isBlank() ? request.reason().trim() : "Assigned to " + waiter.getFullName()
        );
        assignmentRepository.save(newAssignment);

        // 5. Update order direct assigned waiter reference
        order.setAssignedWaiterId(waiter.getId());
        RestaurantOrder savedOrder = orderRepository.save(order);

        log.info("Order {} assigned to waiter {} by user {}", order.getOrderNumber(), waiter.getId(), assigningUserId);
        return orderService.toOrderResponse(savedOrder);
    }

    /**
     * Unassigns the waiter from the order.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderResponse unassignOrder(DineFlowPrincipal principal, UUID orderId, String reason) {
        UUID tenantId = principal.getTenantId();

        RestaurantOrder order = orderRepository.findByIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> new BusinessException("Order not found", HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND"));

        if (principal.getBranchId() != null && order.getBranchId() != null && !order.getBranchId().equals(principal.getBranchId())) {
            throw new BusinessException("Access denied: Order belongs to another branch", HttpStatus.FORBIDDEN, "CROSS_BRANCH_ACCESS_DENIED");
        }

        Optional<RestaurantOrderAssignment> existingActive = assignmentRepository.findByOrderIdAndAssignmentStatus(orderId, "ACTIVE");
        if (existingActive.isPresent()) {
            RestaurantOrderAssignment prev = existingActive.get();
            prev.setAssignmentStatus("UNASSIGNED");
            prev.setUnassignedAt(Instant.now());
            if (reason != null && !reason.isBlank()) {
                prev.setReason(reason.trim());
            }
            assignmentRepository.save(prev);
        }

        order.setAssignedWaiterId(null);
        RestaurantOrder savedOrder = orderRepository.save(order);
        log.info("Order {} unassigned by user {}", order.getOrderNumber(), principal.getUserId());
        return orderService.toOrderResponse(savedOrder);
    }

    /**
     * Gets all orders assigned to the calling waiter.
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersAssignedToWaiter(DineFlowPrincipal principal) {
        UUID tenantId = principal.getTenantId();
        UUID waiterId = principal.getUserId();
        UUID branchId = principal.getBranchId();

        List<RestaurantOrder> orders;
        if (branchId != null) {
            orders = orderRepository.findAllByTenantIdAndBranchIdAndAssignedWaiterIdOrderByCreatedAtDesc(tenantId, branchId, waiterId);
        } else {
            orders = orderRepository.findAllByTenantIdAndAssignedWaiterIdOrderByCreatedAtDesc(tenantId, waiterId);
        }

        return orders.stream().map(orderService::toOrderResponse).toList();
    }

    /**
     * Gets all eligible orders in the branch/tenant for assignment by managers.
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> getBranchOrdersForAssignment(DineFlowPrincipal principal) {
        UUID tenantId = principal.getTenantId();
        UUID branchId = principal.getBranchId();

        List<RestaurantOrder> orders;
        if (branchId != null) {
            orders = orderRepository.findAllByTenantIdAndBranchIdOrderByCreatedAtDesc(tenantId, branchId);
        } else {
            orders = orderRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId, org.springframework.data.domain.PageRequest.of(0, 100)).getContent();
        }

        return orders.stream().map(orderService::toOrderResponse).toList();
    }

    /**
     * Returns full assignment audit history for an order.
     */
    @Transactional(readOnly = true)
    public List<OrderAssignmentResponse> getAssignmentHistory(DineFlowPrincipal principal, UUID orderId) {
        UUID tenantId = principal.getTenantId();

        RestaurantOrder order = orderRepository.findByIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> new BusinessException("Order not found", HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND"));

        if (principal.getBranchId() != null && order.getBranchId() != null && !order.getBranchId().equals(principal.getBranchId())) {
            throw new BusinessException("Access denied: Cross-branch order history", HttpStatus.FORBIDDEN, "CROSS_BRANCH_ACCESS_DENIED");
        }

        List<RestaurantOrderAssignment> history = assignmentRepository.findAllByOrderIdOrderByCreatedAtDesc(orderId);
        return history.stream().map(h -> {
            String waiterName = null;
            if (h.getWaiterId() != null) {
                waiterName = userRepository.findById(h.getWaiterId()).map(User::getFullName).orElse(null);
            }
            return new OrderAssignmentResponse(
                    h.getId(),
                    order.getId(),
                    order.getOrderNumber(),
                    h.getWaiterId(),
                    waiterName,
                    h.getAssignedByUserId(),
                    h.getAssignmentStatus(),
                    h.getReason(),
                    h.getCreatedAt(),
                    h.getUnassignedAt()
            );
        }).toList();
    }
}
