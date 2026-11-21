package com.dineflow.order.service;

import com.dineflow.order.domain.OrderStatus;
import com.dineflow.order.domain.RestaurantOrder;
import com.dineflow.order.domain.RestaurantOrderItem;
import com.dineflow.order.domain.RestaurantOrderItemOption;
import com.dineflow.order.domain.RestaurantOrderStatusHistory;
import com.dineflow.order.dto.KitchenOrderResponse;
import com.dineflow.order.dto.OrderResponse;
import com.dineflow.order.dto.TransitionOrderStatusRequest;
import com.dineflow.order.repository.RestaurantOrderRepository;
import com.dineflow.order.repository.RestaurantOrderStatusHistoryRepository;
import com.dineflow.security.DineFlowPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class KitchenDisplayService {

    private static final List<OrderStatus> KITCHEN_ACTIVE_STATUSES = List.of(
            OrderStatus.ACCEPTED,
            OrderStatus.PREPARING,
            OrderStatus.READY
    );

    private final RestaurantOrderRepository orderRepository;
    private final RestaurantOrderStatusHistoryRepository historyRepository;
    private final OrderLifecycleService lifecycleService;

    public KitchenDisplayService(RestaurantOrderRepository orderRepository,
                                 RestaurantOrderStatusHistoryRepository historyRepository,
                                 OrderLifecycleService lifecycleService) {
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
        this.lifecycleService = lifecycleService;
    }

    /**
     * Retrieves active kitchen orders (ACCEPTED, PREPARING, READY) in FIFO order (oldest first).
     * Enforces tenant and branch boundaries.
     */
    @Transactional(readOnly = true)
    public List<KitchenOrderResponse> getKitchenQueue(DineFlowPrincipal principal) {
        UUID tenantId = principal.getTenantId();
        UUID branchId = principal.getBranchId();

        List<RestaurantOrder> orders;
        if (branchId != null) {
            orders = orderRepository.findAllByTenantIdAndBranchIdAndStatusInOrderByCreatedAtAsc(
                    tenantId, branchId, KITCHEN_ACTIVE_STATUSES);
        } else {
            orders = orderRepository.findAllByTenantIdAndStatusInOrderByCreatedAtAsc(
                    tenantId, KITCHEN_ACTIVE_STATUSES);
        }

        return orders.stream().map(this::toKitchenOrderResponse).toList();
    }

    /**
     * Starts preparation of an order (ACCEPTED -> PREPARING).
     */
    @Transactional
    public OrderResponse startPreparation(DineFlowPrincipal principal, UUID orderId, String note) {
        return lifecycleService.transitionOrderStatus(
                principal,
                orderId,
                new TransitionOrderStatusRequest(OrderStatus.PREPARING, note != null ? note : "Kitchen started cooking")
        );
    }

    /**
     * Marks preparation complete and order ready for pickup (PREPARING -> READY).
     */
    @Transactional
    public OrderResponse markReady(DineFlowPrincipal principal, UUID orderId, String note) {
        return lifecycleService.transitionOrderStatus(
                principal,
                orderId,
                new TransitionOrderStatusRequest(OrderStatus.READY, note != null ? note : "Plated and ready for pickup")
        );
    }

    public KitchenOrderResponse toKitchenOrderResponse(RestaurantOrder order) {
        Instant acceptedAt = null;
        if (order.getStatusHistory() != null) {
            for (RestaurantOrderStatusHistory h : order.getStatusHistory()) {
                if ("ACCEPTED".equals(h.getNewStatus())) {
                    acceptedAt = h.getCreatedAt();
                    break;
                }
            }
        }

        List<KitchenOrderResponse.KitchenItemResponse> itemResponses = new ArrayList<>();
        if (order.getItems() != null) {
            for (RestaurantOrderItem item : order.getItems()) {
                List<String> options = new ArrayList<>();
                if (item.getOptions() != null) {
                    for (RestaurantOrderItemOption opt : item.getOptions()) {
                        options.add(opt.getOptionNameSnapshot());
                    }
                }

                itemResponses.add(new KitchenOrderResponse.KitchenItemResponse(
                        item.getId(),
                        item.getItemNameSnapshot(),
                        item.getVariantNameSnapshot(),
                        item.getQuantity(),
                        item.getSpecialInstructions(),
                        options
                ));
            }
        }

        return new KitchenOrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getTableNumber(),
                order.getStatus(),
                order.getBranchId(),
                order.getTotalItemCount(),
                order.getSpecialNotes(),
                order.getCreatedAt(),
                acceptedAt,
                itemResponses
        );
    }
}
