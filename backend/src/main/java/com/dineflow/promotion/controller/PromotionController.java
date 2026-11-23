package com.dineflow.promotion.controller;

import com.dineflow.common.api.ApiResponse;
import com.dineflow.promotion.dto.ApplyCouponRequest;
import com.dineflow.promotion.dto.CouponApplicationResponse;
import com.dineflow.promotion.dto.CreatePromotionRequest;
import com.dineflow.promotion.dto.PromotionAnalyticsResponse;
import com.dineflow.promotion.dto.PromotionResponse;
import com.dineflow.promotion.dto.RedemptionResponse;
import com.dineflow.promotion.dto.UpdatePromotionRequest;
import com.dineflow.promotion.service.PromotionEngineService;
import com.dineflow.security.DineFlowPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/promotions")
public class PromotionController {

    private final PromotionEngineService promotionService;

    public PromotionController(PromotionEngineService promotionService) {
        this.promotionService = promotionService;
    }

    // =========================================================================
    // ADMIN MANAGEMENT ENDPOINTS
    // =========================================================================

    @PostMapping
    @PreAuthorize("hasAuthority('PROMOTION_MANAGE') or hasAuthority('HOTEL_ADMIN') or hasAuthority('HOTEL_OWNER')")
    public ResponseEntity<ApiResponse<PromotionResponse>> createPromotion(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @Valid @RequestBody CreatePromotionRequest request) {
        PromotionResponse response = promotionService.createPromotion(principal, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Promotion created successfully"));
    }

    @PutMapping("/{promotionId}")
    @PreAuthorize("hasAuthority('PROMOTION_MANAGE') or hasAuthority('HOTEL_ADMIN') or hasAuthority('HOTEL_OWNER')")
    public ResponseEntity<ApiResponse<PromotionResponse>> updatePromotion(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @PathVariable("promotionId") UUID promotionId,
            @Valid @RequestBody UpdatePromotionRequest request) {
        PromotionResponse response = promotionService.updatePromotion(principal, promotionId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Promotion updated successfully"));
    }

    @PatchMapping("/{promotionId}/status")
    @PreAuthorize("hasAuthority('PROMOTION_MANAGE') or hasAuthority('HOTEL_ADMIN') or hasAuthority('HOTEL_OWNER')")
    public ResponseEntity<ApiResponse<PromotionResponse>> togglePromotionStatus(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @PathVariable("promotionId") UUID promotionId,
            @RequestParam("active") boolean active) {
        PromotionResponse response = promotionService.setPromotionActive(principal, promotionId, active);
        return ResponseEntity.ok(ApiResponse.ok(response, active ? "Promotion activated" : "Promotion deactivated"));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PROMOTION_VIEW') or hasAuthority('HOTEL_ADMIN') or hasAuthority('HOTEL_OWNER')")
    public ResponseEntity<ApiResponse<List<PromotionResponse>>> listPromotions(
            @AuthenticationPrincipal DineFlowPrincipal principal) {
        List<PromotionResponse> responses = promotionService.listPromotions(principal);
        return ResponseEntity.ok(ApiResponse.ok(responses, "Promotions retrieved"));
    }

    @GetMapping("/{promotionId}/redemptions")
    @PreAuthorize("hasAuthority('PROMOTION_VIEW') or hasAuthority('HOTEL_ADMIN') or hasAuthority('HOTEL_OWNER')")
    public ResponseEntity<ApiResponse<List<RedemptionResponse>>> getPromotionRedemptions(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @PathVariable("promotionId") UUID promotionId) {
        List<RedemptionResponse> responses = promotionService.getPromotionRedemptions(principal, promotionId);
        return ResponseEntity.ok(ApiResponse.ok(responses, "Promotion redemptions retrieved"));
    }

    @GetMapping("/analytics")
    @PreAuthorize("hasAuthority('PROMOTION_VIEW') or hasAuthority('HOTEL_ADMIN') or hasAuthority('HOTEL_OWNER')")
    public ResponseEntity<ApiResponse<PromotionAnalyticsResponse>> getAnalytics(
            @AuthenticationPrincipal DineFlowPrincipal principal) {
        PromotionAnalyticsResponse analytics = promotionService.getAnalytics(principal);
        return ResponseEntity.ok(ApiResponse.ok(analytics, "Promotion analytics retrieved"));
    }

    // =========================================================================
    // CUSTOMER & STAFF COUPON VALIDATION / APPLICATION ENDPOINTS
    // =========================================================================

    @PostMapping("/bills/{billId}/validate")
    @PreAuthorize("hasAuthority('PROMOTION_APPLY') or hasAuthority('CUSTOMER_ORDER_VIEW_OWN') or hasAuthority('BILL_VIEW')")
    public ResponseEntity<ApiResponse<CouponApplicationResponse>> validateCoupon(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @PathVariable("billId") UUID billId,
            @Valid @RequestBody ApplyCouponRequest request) {
        CouponApplicationResponse response = promotionService.validateCouponForBill(principal, billId, request.couponCode());
        return ResponseEntity.ok(ApiResponse.ok(response, response.message()));
    }

    @PostMapping("/bills/{billId}/apply")
    @PreAuthorize("hasAuthority('PROMOTION_APPLY') or hasAuthority('CUSTOMER_ORDER_VIEW_OWN') or hasAuthority('BILL_COLLECT_PAYMENT')")
    public ResponseEntity<ApiResponse<CouponApplicationResponse>> applyCoupon(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @PathVariable("billId") UUID billId,
            @Valid @RequestBody ApplyCouponRequest request) {
        CouponApplicationResponse response = promotionService.applyCouponToBill(principal, billId, request.couponCode());
        return ResponseEntity.ok(ApiResponse.ok(response, response.message()));
    }

    @PostMapping("/bills/{billId}/remove")
    @PreAuthorize("hasAuthority('PROMOTION_APPLY') or hasAuthority('CUSTOMER_ORDER_VIEW_OWN') or hasAuthority('BILL_COLLECT_PAYMENT')")
    public ResponseEntity<ApiResponse<CouponApplicationResponse>> removeCoupon(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @PathVariable("billId") UUID billId,
            @RequestParam(value = "couponCode", required = false) String couponCode) {
        CouponApplicationResponse response = promotionService.removeCouponFromBill(principal, billId, couponCode);
        return ResponseEntity.ok(ApiResponse.ok(response, response.message()));
    }
}
