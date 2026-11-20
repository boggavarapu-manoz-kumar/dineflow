package com.dineflow.identity.controller;

import com.dineflow.common.api.ApiResponse;
import com.dineflow.common.exception.BusinessException;
import com.dineflow.identity.dto.CustomerSessionRequest;
import com.dineflow.identity.dto.CustomerSessionResponse;
import com.dineflow.identity.service.AuthService;
import com.dineflow.security.DineFlowPrincipal;
import com.dineflow.security.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customer-sessions")
public class CustomerSessionController {

    private final AuthService authService;
    private final RateLimiterService rateLimiterService;

    public CustomerSessionController(AuthService authService, RateLimiterService rateLimiterService) {
        this.authService = authService;
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CustomerSessionResponse>> createCustomerSession(
            @Valid @RequestBody CustomerSessionRequest request,
            HttpServletRequest servletRequest) {
        if (!rateLimiterService.tryAcquire("guest:" + servletRequest.getRemoteAddr(), 20, 60)) {
            throw new BusinessException("Too many session creation requests", HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED");
        }

        CustomerSessionResponse response = authService.createCustomerSession(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Guest dining session established"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Map<String, String>>> logoutCustomerSession() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("status", "GUEST_SESSION_TERMINATED"), "Guest session ended"));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('CUSTOMER_ORDER_VIEW_OWN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getGuestSession(
            @AuthenticationPrincipal DineFlowPrincipal principal) {
        Map<String, Object> session = Map.of(
                "sessionId", principal.getUserId(),
                "tenantId", principal.getTenantId() != null ? principal.getTenantId() : "NONE",
                "role", principal.getRoleCode(),
                "type", principal.getType()
        );
        return ResponseEntity.ok(ApiResponse.ok(session, "Guest session active"));
    }

    /**
     * Demonstrates customer session ownership protection:
     * Guest can only access orders for their own session.
     */
    @GetMapping("/{sessionId}/orders")
    @PreAuthorize("@tenantSecurity.isGuestSessionOwner(#sessionId) and hasAuthority('CUSTOMER_ORDER_VIEW_OWN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getGuestOwnOrders(
            @PathVariable UUID sessionId) {
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("sessionId", sessionId, "orders", "Own dining table order items"),
                "Customer guest orders retrieved"
        ));
    }
}