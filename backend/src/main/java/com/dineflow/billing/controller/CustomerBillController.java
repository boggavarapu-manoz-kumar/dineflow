package com.dineflow.billing.controller;

import com.dineflow.billing.dto.BillResponse;
import com.dineflow.billing.service.BillingEngineService;
import com.dineflow.common.api.ApiResponse;
import com.dineflow.security.DineFlowPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customer/bills")
public class CustomerBillController {

    private final BillingEngineService billingService;

    public CustomerBillController(BillingEngineService billingService) {
        this.billingService = billingService;
    }

    @GetMapping("/current")
    @PreAuthorize("hasAuthority('CUSTOMER_ORDER_VIEW_OWN')")
    public ResponseEntity<ApiResponse<BillResponse>> getCurrentBill(
            @AuthenticationPrincipal DineFlowPrincipal principal) {
        BillResponse response = billingService.getCustomerCurrentBill(principal);
        return ResponseEntity.ok(ApiResponse.ok(response, "Current dining bill retrieved"));
    }

    @GetMapping("/{billId}")
    @PreAuthorize("hasAuthority('CUSTOMER_ORDER_VIEW_OWN')")
    public ResponseEntity<ApiResponse<BillResponse>> getBill(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @PathVariable("billId") UUID billId) {
        BillResponse response = billingService.getBill(principal, billId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Bill retrieved"));
    }
}
