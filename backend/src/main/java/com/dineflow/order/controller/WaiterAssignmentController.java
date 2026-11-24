package com.dineflow.order.controller;

import com.dineflow.common.api.ApiResponse;
import com.dineflow.order.dto.AssignWaiterRequest;
import com.dineflow.order.dto.OrderAssignmentResponse;
import com.dineflow.order.dto.OrderResponse;
import com.dineflow.order.service.WaiterAssignmentService;
import com.dineflow.security.DineFlowPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/waiter")
public class WaiterAssignmentController {

    private final WaiterAssignmentService assignmentService;

    public WaiterAssignmentController(WaiterAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    /**
     * Waiter personal queue: view orders assigned to the calling waiter.
     */
    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('WAITER_VIEW') or hasAuthority('ORDER_VIEW')")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getMyAssignedOrders(
            @AuthenticationPrincipal DineFlowPrincipal principal) {
        List<OrderResponse> orders = assignmentService.getOrdersAssignedToWaiter(principal);
        return ResponseEntity.ok(ApiResponse.ok(orders, "Waiter assigned orders retrieved"));
    }

    /**
     * Dispatch queue: view eligible branch orders for waiter assignment.
     */
    @GetMapping("/dispatch-queue")
    @PreAuthorize("hasAuthority('WAITER_ASSIGN') or hasAuthority('WAITER_VIEW') or hasAuthority('ORDER_MANAGE')")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getDispatchQueue(
            @AuthenticationPrincipal DineFlowPrincipal principal) {
        List<OrderResponse> orders = assignmentService.getBranchOrdersForAssignment(principal);
        return ResponseEntity.ok(ApiResponse.ok(orders, "Dispatch queue orders retrieved"));
    }

    /**
     * Assign or reassign order to a waiter.
     */
    @PostMapping("/orders/{orderId}/assign")
    @PreAuthorize("hasAuthority('WAITER_ASSIGN') or hasAuthority('ORDER_MANAGE')")
    public ResponseEntity<ApiResponse<OrderResponse>> assignOrder(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @PathVariable("orderId") UUID orderId,
            @Valid @RequestBody AssignWaiterRequest request) {
        OrderResponse response = assignmentService.assignOrderToWaiter(principal, orderId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Order assigned to waiter successfully"));
    }

    /**
     * Remove waiter assignment from an order.
     */
    @DeleteMapping("/orders/{orderId}/assign")
    @PreAuthorize("hasAuthority('WAITER_ASSIGN') or hasAuthority('ORDER_MANAGE')")
    public ResponseEntity<ApiResponse<OrderResponse>> unassignOrder(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @PathVariable("orderId") UUID orderId,
            @RequestParam(value = "reason", required = false) String reason) {
        OrderResponse response = assignmentService.unassignOrder(principal, orderId, reason);
        return ResponseEntity.ok(ApiResponse.ok(response, "Order assignment removed"));
    }

    /**
     * Get assignment audit history for an order.
     */
    @GetMapping("/orders/{orderId}/history")
    @PreAuthorize("hasAuthority('WAITER_ASSIGN') or hasAuthority('ORDER_VIEW')")
    public ResponseEntity<ApiResponse<List<OrderAssignmentResponse>>> getAssignmentHistory(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @PathVariable("orderId") UUID orderId) {
        List<OrderAssignmentResponse> history = assignmentService.getAssignmentHistory(principal, orderId);
        return ResponseEntity.ok(ApiResponse.ok(history, "Order assignment history retrieved"));
    }
}
