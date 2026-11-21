package com.dineflow.order.controller;

import com.dineflow.common.api.ApiResponse;
import com.dineflow.order.dto.CreateOrderRequest;
import com.dineflow.order.dto.OrderResponse;
import com.dineflow.order.service.OrderService;
import com.dineflow.security.DineFlowPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customer/orders")
public class CustomerOrderController {

    private final OrderService orderService;
    private final com.dineflow.order.service.OrderLifecycleService lifecycleService;

    public CustomerOrderController(OrderService orderService,
                                   com.dineflow.order.service.OrderLifecycleService lifecycleService) {
        this.orderService = orderService;
        this.lifecycleService = lifecycleService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CUSTOMER_ORDER_CREATE')")
    public ResponseEntity<ApiResponse<OrderResponse>> submitOrder(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody(required = false) CreateOrderRequest request) {
        OrderResponse response = orderService.createOrderFromCart(principal, idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Order placed successfully"));
    }

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasAuthority('CUSTOMER_ORDER_VIEW_OWN')")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @PathVariable("orderId") UUID orderId,
            @Valid @RequestBody(required = false) com.dineflow.order.dto.CancelOrderRequest request) {
        OrderResponse response = lifecycleService.cancelOrderAsCustomer(principal, orderId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Order cancelled successfully"));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAuthority('CUSTOMER_ORDER_VIEW_OWN')")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @PathVariable("orderId") UUID orderId) {
        OrderResponse response = orderService.getCustomerOrder(principal, orderId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Order retrieved successfully"));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_ORDER_VIEW_OWN')")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getMyOrders(
            @AuthenticationPrincipal DineFlowPrincipal principal) {
        List<OrderResponse> orders = orderService.getCustomerOrders(principal);
        return ResponseEntity.ok(ApiResponse.ok(orders, "Customer orders retrieved successfully"));
    }
}
