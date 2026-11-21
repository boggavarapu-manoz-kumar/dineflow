package com.dineflow.order.controller;

import com.dineflow.common.api.ApiResponse;
import com.dineflow.order.dto.CancelOrderRequest;
import com.dineflow.order.dto.OrderResponse;
import com.dineflow.order.dto.OrderStatusHistoryResponse;
import com.dineflow.order.dto.TransitionOrderStatusRequest;
import com.dineflow.order.service.OrderLifecycleService;
import com.dineflow.security.DineFlowPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class StaffOrderController {

    private final OrderLifecycleService lifecycleService;

    public StaffOrderController(OrderLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @PostMapping("/{orderId}/transition")
    @PreAuthorize("hasAuthority('ORDER_VIEW') or hasAuthority('KITCHEN_VIEW') or hasAuthority('WAITER_VIEW')")
    public ResponseEntity<ApiResponse<OrderResponse>> transitionStatus(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @PathVariable("orderId") UUID orderId,
            @Valid @RequestBody TransitionOrderStatusRequest request) {
        OrderResponse response = lifecycleService.transitionOrderStatus(principal, orderId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Order transitioned to " + response.status()));
    }

    @GetMapping("/{orderId}/history")
    @PreAuthorize("hasAuthority('ORDER_VIEW') or hasAuthority('KITCHEN_VIEW') or hasAuthority('WAITER_VIEW') or hasAuthority('CUSTOMER_ORDER_VIEW_OWN')")
    public ResponseEntity<ApiResponse<List<OrderStatusHistoryResponse>>> getStatusHistory(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @PathVariable("orderId") UUID orderId) {
        List<OrderStatusHistoryResponse> history = lifecycleService.getOrderStatusHistory(principal, orderId);
        return ResponseEntity.ok(ApiResponse.ok(history, "Order audit history retrieved"));
    }
}
