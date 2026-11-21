package com.dineflow.cart.controller;

import com.dineflow.cart.dto.AddCartItemRequest;
import com.dineflow.cart.dto.CartResponse;
import com.dineflow.cart.dto.UpdateCartItemQuantityRequest;
import com.dineflow.cart.service.CustomerCartService;
import com.dineflow.common.api.ApiResponse;
import com.dineflow.security.DineFlowPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customer/cart")
public class CustomerCartController {

    private final CustomerCartService cartService;

    public CustomerCartController(CustomerCartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_MENU_VIEW') or hasAuthority('CUSTOMER_ORDER_CREATE')")
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            @AuthenticationPrincipal DineFlowPrincipal principal) {
        CartResponse cart = cartService.getOrCreateCart(principal);
        return ResponseEntity.ok(ApiResponse.ok(cart, "Customer cart retrieved successfully"));
    }

    @PostMapping("/items")
    @PreAuthorize("hasAuthority('CUSTOMER_MENU_VIEW') or hasAuthority('CUSTOMER_ORDER_CREATE')")
    public ResponseEntity<ApiResponse<CartResponse>> addItemToCart(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @Valid @RequestBody AddCartItemRequest request) {
        CartResponse cart = cartService.addItemToCart(principal, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(cart, "Item added to cart"));
    }

    @PatchMapping("/items/{cartItemId}")
    @PreAuthorize("hasAuthority('CUSTOMER_MENU_VIEW') or hasAuthority('CUSTOMER_ORDER_CREATE')")
    public ResponseEntity<ApiResponse<CartResponse>> updateItemQuantity(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @PathVariable("cartItemId") UUID cartItemId,
            @Valid @RequestBody UpdateCartItemQuantityRequest request) {
        CartResponse cart = cartService.updateItemQuantity(principal, cartItemId, request);
        return ResponseEntity.ok(ApiResponse.ok(cart, "Cart item quantity updated"));
    }

    @DeleteMapping("/items/{cartItemId}")
    @PreAuthorize("hasAuthority('CUSTOMER_MENU_VIEW') or hasAuthority('CUSTOMER_ORDER_CREATE')")
    public ResponseEntity<ApiResponse<CartResponse>> removeItemFromCart(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @PathVariable("cartItemId") UUID cartItemId) {
        CartResponse cart = cartService.removeItemFromCart(principal, cartItemId);
        return ResponseEntity.ok(ApiResponse.ok(cart, "Item removed from cart"));
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('CUSTOMER_MENU_VIEW') or hasAuthority('CUSTOMER_ORDER_CREATE')")
    public ResponseEntity<ApiResponse<CartResponse>> clearCart(
            @AuthenticationPrincipal DineFlowPrincipal principal) {
        CartResponse cart = cartService.clearCart(principal);
        return ResponseEntity.ok(ApiResponse.ok(cart, "Cart cleared successfully"));
    }

    @PostMapping("/validate-price")
    @PreAuthorize("hasAuthority('CUSTOMER_MENU_VIEW') or hasAuthority('CUSTOMER_ORDER_CREATE')")
    public ResponseEntity<ApiResponse<com.dineflow.pricing.dto.PriceVerificationResult>> validatePrice(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @RequestBody(required = false) com.dineflow.pricing.dto.ValidateCartPricingRequest request) {
        java.math.BigDecimal expectedTotal = request != null ? request.expectedGrandTotal() : null;
        com.dineflow.pricing.dto.PriceVerificationResult result = cartService.verifyCartPrice(principal, expectedTotal);
        return ResponseEntity.ok(ApiResponse.ok(result, "Cart price integrity verified"));
    }
}
