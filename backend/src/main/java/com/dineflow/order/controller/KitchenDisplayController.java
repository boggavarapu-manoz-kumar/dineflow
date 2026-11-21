package com.dineflow.order.controller;

import com.dineflow.common.api.ApiResponse;
import com.dineflow.order.dto.KitchenOrderResponse;
import com.dineflow.order.dto.OrderResponse;
import com.dineflow.order.service.KitchenDisplayService;
import com.dineflow.security.DineFlowPrincipal;
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
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kitchen")
public class KitchenDisplayController {

    private final KitchenDisplayService kitchenService;

    public KitchenDisplayController(KitchenDisplayService kitchenService) {
        this.kitchenService = kitchenService;
    }

    /**
     * Kitchen active preparation queue (ACCEPTED, PREPARING, READY) in chronological order.
     */
    @GetMapping("/queue")
    @PreAuthorize("hasAuthority('KITCHEN_VIEW') or hasAuthority('ORDER_MANAGE')")
    public ResponseEntity<ApiResponse<List<KitchenOrderResponse>>> getKitchenQueue(
            @AuthenticationPrincipal DineFlowPrincipal principal) {
        List<KitchenOrderResponse> queue = kitchenService.getKitchenQueue(principal);
        return ResponseEntity.ok(ApiResponse.ok(queue, "Kitchen preparation queue retrieved"));
    }

    /**
     * Start cooking / preparing an order.
     */
    @PostMapping("/orders/{orderId}/prepare")
    @PreAuthorize("hasAuthority('KITCHEN_UPDATE') or hasAuthority('ORDER_MANAGE')")
    public ResponseEntity<ApiResponse<OrderResponse>> startPreparation(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @PathVariable("orderId") UUID orderId,
            @RequestBody(required = false) Map<String, String> body) {
        String note = body != null ? body.get("note") : null;
        OrderResponse response = kitchenService.startPreparation(principal, orderId, note);
        return ResponseEntity.ok(ApiResponse.ok(response, "Order is now in PREPARING status"));
    }

    /**
     * Mark cooking complete and dish ready for waitstaff pickup.
     */
    @PostMapping("/orders/{orderId}/ready")
    @PreAuthorize("hasAuthority('KITCHEN_UPDATE') or hasAuthority('ORDER_MANAGE')")
    public ResponseEntity<ApiResponse<OrderResponse>> markReady(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @PathVariable("orderId") UUID orderId,
            @RequestBody(required = false) Map<String, String> body) {
        String note = body != null ? body.get("note") : null;
        OrderResponse response = kitchenService.markReady(principal, orderId, note);
        return ResponseEntity.ok(ApiResponse.ok(response, "Order is now in READY status"));
    }
}
