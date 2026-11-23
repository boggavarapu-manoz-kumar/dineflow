package com.dineflow.payment.controller;

import com.dineflow.common.api.ApiResponse;
import com.dineflow.payment.domain.RefundStatus;
import com.dineflow.payment.dto.RefundResponse;
import com.dineflow.payment.dto.RequestRefundRequest;
import com.dineflow.payment.service.RefundManagementService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments/refunds")
public class RefundController {

    private final RefundManagementService refundService;

    public RefundController(RefundManagementService refundService) {
        this.refundService = refundService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PAYMENT_VIEW') or hasAuthority('BILL_VIEW')")
    public ResponseEntity<ApiResponse<List<RefundResponse>>> listRefunds(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @RequestParam(value = "status", required = false) RefundStatus status) {
        List<RefundResponse> responses = refundService.listRefunds(principal, status);
        return ResponseEntity.ok(ApiResponse.ok(responses, "Refunds retrieved"));
    }

    @PostMapping("/request")
    @PreAuthorize("hasAuthority('REFUND_REQUEST') or hasAuthority('HOTEL_ADMIN') or hasAuthority('HOTEL_OWNER')")
    public ResponseEntity<ApiResponse<RefundResponse>> requestRefund(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @Valid @RequestBody RequestRefundRequest request) {
        RefundResponse response = refundService.requestRefund(principal, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Refund requested successfully"));
    }

    @PostMapping("/{refundId}/approve")
    @PreAuthorize("hasAuthority('REFUND_APPROVE') or hasAuthority('HOTEL_ADMIN') or hasAuthority('HOTEL_OWNER')")
    public ResponseEntity<ApiResponse<RefundResponse>> approveRefund(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @PathVariable("refundId") UUID refundId) {
        RefundResponse response = refundService.approveRefund(principal, refundId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Refund approved"));
    }

    @PostMapping("/{refundId}/execute")
    @PreAuthorize("hasAuthority('REFUND_EXECUTE') or hasAuthority('HOTEL_ADMIN') or hasAuthority('HOTEL_OWNER')")
    public ResponseEntity<ApiResponse<RefundResponse>> executeRefund(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @PathVariable("refundId") UUID refundId) {
        RefundResponse response = refundService.executeRefund(principal, refundId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Refund executed and balance adjusted"));
    }

    @GetMapping("/payment/{paymentId}")
    @PreAuthorize("hasAuthority('PAYMENT_VIEW') or hasAuthority('BILL_VIEW')")
    public ResponseEntity<ApiResponse<List<RefundResponse>>> getRefundsForPayment(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @PathVariable("paymentId") UUID paymentId) {
        List<RefundResponse> responses = refundService.getRefundsForPayment(principal, paymentId);
        return ResponseEntity.ok(ApiResponse.ok(responses, "Payment refund history retrieved"));
    }
}
