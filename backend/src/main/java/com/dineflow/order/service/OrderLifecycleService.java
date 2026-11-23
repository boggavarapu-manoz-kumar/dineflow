package com.dineflow.order.service;

import com.dineflow.common.exception.BusinessException;
import com.dineflow.order.domain.OrderStatus;
import com.dineflow.order.domain.RestaurantOrder;
import com.dineflow.order.domain.RestaurantOrderStatusHistory;
import com.dineflow.order.dto.CancelOrderRequest;
import com.dineflow.order.dto.OrderResponse;
import com.dineflow.order.dto.OrderStatusHistoryResponse;
import com.dineflow.order.dto.TransitionOrderStatusRequest;
import com.dineflow.order.repository.RestaurantOrderRepository;
import com.dineflow.order.repository.RestaurantOrderStatusHistoryRepository;
import com.dineflow.realtime.RealtimeEvent;
import com.dineflow.security.DineFlowPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(OrderLifecycleService.class);

    private final RestaurantOrderRepository orderRepository;
    private final RestaurantOrderStatusHistoryRepository historyRepository;
    private final OrderService orderService;
    private final ApplicationEventPublisher eventPublisher;

    // Transition state machine: Source Status -> Permitted Target Statuses
    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = Map.of(
            OrderStatus.PLACED, Set.of(OrderStatus.ACCEPTED, OrderStatus.CANCELLED),
            OrderStatus.ACCEPTED, Set.of(OrderStatus.PREPARING, OrderStatus.CANCELLED),
            OrderStatus.PREPARING, Set.of(OrderStatus.READY, OrderStatus.CANCELLED),
            OrderStatus.READY, Set.of(OrderStatus.SERVED),
            OrderStatus.SERVED, Set.of(OrderStatus.PAID),
            OrderStatus.PAID, Set.of(),     // Terminal state
            OrderStatus.CANCELLED, Set.of() // Terminal state
    );

    // Required authorities for target statuses: Target Status -> Permitted Authorities
    private static final Map<OrderStatus, Set<String>> REQUIRED_AUTHORITIES = Map.of(
            OrderStatus.ACCEPTED, Set.of("ORDER_ACCEPT", "ORDER_MANAGE"),
            OrderStatus.PREPARING, Set.of("KITCHEN_UPDATE", "ORDER_MANAGE"),
            OrderStatus.READY, Set.of("KITCHEN_UPDATE", "ORDER_MANAGE"),
            OrderStatus.SERVED, Set.of("ORDER_SERVE", "WAITER_VIEW", "ORDER_MANAGE"),
            OrderStatus.PAID, Set.of("BILL_MANAGE", "ORDER_MANAGE")
    );

    public OrderLifecycleService(RestaurantOrderRepository orderRepository,
                                 RestaurantOrderStatusHistoryRepository historyRepository,
                                 OrderService orderService,
                                 ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
        this.orderService = orderService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Executes a staff-driven order state transition with strict RBAC, branch isolation, and optimistic concurrency.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderResponse transitionOrderStatus(DineFlowPrincipal principal, UUID orderId, TransitionOrderStatusRequest request) {
        RestaurantOrder order = loadAndValidateScopedOrder(principal, orderId);
        OrderStatus currentStatus = order.getStatus();
        OrderStatus targetStatus = request.targetStatus();

        // 1. Verify transition is legal in state machine
        validateTransitionLegality(currentStatus, targetStatus);

        // 2. Cancellation validation (Staff cancellation requires reason and ORDER_CANCEL or ORDER_MANAGE)
        if (targetStatus == OrderStatus.CANCELLED) {
            validateStaffCancellation(principal, request.reason());
        } else {
            // 3. Verify actor has required permission for target state
            validateStaffPermission(principal, targetStatus);
        }

        // 4. Update status and record audit history
        order.setStatus(targetStatus);
        RestaurantOrderStatusHistory history = new RestaurantOrderStatusHistory(
                order,
                currentStatus.name(),
                targetStatus.name(),
                principal.getUserId(),
                principal.getRoleCode() != null ? principal.getRoleCode() : "STAFF",
                principal.getType() != null ? principal.getType() : "STAFF",
                request.reason() != null && !request.reason().isBlank() ? request.reason().trim() : "Transitioned to " + targetStatus
        );
        order.addStatusHistory(history);

        try {
            RestaurantOrder savedOrder = orderRepository.saveAndFlush(order);
            publishOrderTransitionEvents(savedOrder, currentStatus, targetStatus);
            log.info("Order status transitioned: OrderId: {}, OrderNumber: {}, From: {}, To: {}, Actor: {}",
                    savedOrder.getId(), savedOrder.getOrderNumber(), currentStatus, targetStatus, principal.getUserId());
            return orderService.toOrderResponse(savedOrder);
        } catch (OptimisticLockingFailureException ex) {
            log.warn("Concurrent modification detected for order {}: {}", orderId, ex.getMessage());
            throw new BusinessException("Order was modified concurrently by another staff member. Please refresh and try again.",
                    HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION");
        }
    }

    /**
     * Guest customer order cancellation: Allowed only while the order is in PLACED stage.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderResponse cancelOrderAsCustomer(DineFlowPrincipal principal, UUID orderId, CancelOrderRequest request) {
        UUID tenantId = principal.getTenantId();
        UUID sessionId = principal.getUserId();

        RestaurantOrder order = orderRepository.findByIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> new BusinessException("Order not found", HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND"));

        // Only allow customer to cancel their own session's order
        if (!order.getCustomerSessionId().equals(sessionId)) {
            throw new BusinessException("Access denied: You do not own this dining order", HttpStatus.FORBIDDEN, "ORDER_ACCESS_DENIED");
        }

        // Customers can ONLY cancel if the order is still in PLACED state
        if (order.getStatus() != OrderStatus.PLACED) {
            throw new BusinessException("Order cannot be cancelled because the kitchen has already accepted or begun preparing it.",
                    HttpStatus.CONFLICT, "CANNOT_CANCEL_ORDER");
        }

        OrderStatus currentStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);

        RestaurantOrderStatusHistory history = new RestaurantOrderStatusHistory(
                order,
                currentStatus.name(),
                OrderStatus.CANCELLED.name(),
                sessionId,
                "CUSTOMER_GUEST",
                "GUEST",
                request != null && request.reason() != null && !request.reason().isBlank()
                        ? request.reason().trim() : "Cancelled by customer before kitchen acceptance"
        );
        order.addStatusHistory(history);

        try {
            RestaurantOrder savedOrder = orderRepository.saveAndFlush(order);
            log.info("Order cancelled by customer: OrderId: {}, SessionId: {}", savedOrder.getId(), sessionId);
            return orderService.toOrderResponse(savedOrder);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException("Order was modified concurrently. Please refresh.",
                    HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION");
        }
    }

    /**
     * Retrieves the complete immutable audit history for an order.
     */
    @Transactional(readOnly = true)
    public List<OrderStatusHistoryResponse> getOrderStatusHistory(DineFlowPrincipal principal, UUID orderId) {
        RestaurantOrder order = loadAndValidateScopedOrder(principal, orderId);

        List<RestaurantOrderStatusHistory> histories = historyRepository.findAllByOrderIdOrderByCreatedAtAsc(order.getId());
        return histories.stream()
                .map(h -> new OrderStatusHistoryResponse(
                        h.getId(),
                        order.getId(),
                        h.getPreviousStatus(),
                        h.getNewStatus(),
                        h.getActorType(),
                        h.getChangedByUserId(),
                        h.getChangedByRole(),
                        h.getReason(),
                        h.getCreatedAt()
                ))
                .toList();
    }

    private RestaurantOrder loadAndValidateScopedOrder(DineFlowPrincipal principal, UUID orderId) {
        UUID tenantId = principal.getTenantId();
        UUID userBranchId = principal.getBranchId();

        RestaurantOrder order = orderRepository.findByIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> new BusinessException("Order not found", HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND"));

        // If staff is branch-scoped, order must belong to their branch
        if (userBranchId != null && order.getBranchId() != null && !order.getBranchId().equals(userBranchId)) {
            throw new BusinessException("Access denied: Order belongs to a different branch", HttpStatus.FORBIDDEN, "CROSS_BRANCH_ACCESS_DENIED");
        }

        return order;
    }

    private void validateTransitionLegality(OrderStatus current, OrderStatus target) {
        if (current == target) {
            throw new BusinessException("Order is already in " + target + " status", HttpStatus.BAD_REQUEST, "IDENTICAL_STATUS");
        }

        Set<OrderStatus> allowed = VALID_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw new BusinessException("Illegal status transition from " + current + " to " + target,
                    HttpStatus.BAD_REQUEST, "ILLEGAL_STATUS_TRANSITION");
        }
    }

    private void validateStaffPermission(DineFlowPrincipal principal, OrderStatus targetStatus) {
        Set<String> required = REQUIRED_AUTHORITIES.getOrDefault(targetStatus, Set.of("ORDER_MANAGE"));
        boolean hasPermission = principal.getAuthorities().stream()
                .anyMatch(a -> required.contains(a.getAuthority()) || "PLATFORM_MANAGE".equals(a.getAuthority()));

        if (!hasPermission) {
            throw new BusinessException("User does not have permission to transition order to " + targetStatus,
                    HttpStatus.FORBIDDEN, "PERMISSION_DENIED");
        }
    }

    private void validateStaffCancellation(DineFlowPrincipal principal, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("A cancellation reason is required", HttpStatus.BAD_REQUEST, "CANCELLATION_REASON_REQUIRED");
        }

        boolean hasCancelPermission = principal.getAuthorities().stream()
                .anyMatch(a -> "ORDER_CANCEL".equals(a.getAuthority()) || "ORDER_MANAGE".equals(a.getAuthority()) || "PLATFORM_MANAGE".equals(a.getAuthority()));

        if (!hasCancelPermission) {
            throw new BusinessException("User does not have permission to cancel orders", HttpStatus.FORBIDDEN, "PERMISSION_DENIED");
        }
    }

    private void publishOrderTransitionEvents(RestaurantOrder order, OrderStatus fromStatus, OrderStatus toStatus) {
        String eventType = "ORDER_" + toStatus.name();
        Map<String, Object> safePayload = Map.of(
                "orderId", order.getId().toString(),
                "orderNumber", order.getOrderNumber(),
                "tableNumber", order.getTableNumber() != null ? order.getTableNumber() : "",
                "previousStatus", fromStatus.name(),
                "status", toStatus.name()
        );
        Long domainVersion = order.getVersion();

        eventPublisher.publishEvent(RealtimeEvent.ofVersioned(
                eventType, order.getTenantId(), order.getBranchId(), "order", order.getId(), domainVersion, safePayload));
        eventPublisher.publishEvent(RealtimeEvent.ofVersioned(
                eventType, order.getTenantId(), order.getBranchId(), "waiter", order.getId(), domainVersion, safePayload));

        if (toStatus == OrderStatus.ACCEPTED || toStatus == OrderStatus.PREPARING
                || toStatus == OrderStatus.READY || toStatus == OrderStatus.CANCELLED) {
            eventPublisher.publishEvent(RealtimeEvent.ofVersioned(
                    eventType, order.getTenantId(), order.getBranchId(), "kitchen", order.getId(), domainVersion, safePayload));
        }
    }
}
