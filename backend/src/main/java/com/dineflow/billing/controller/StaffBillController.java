package com.dineflow.billing.controller;

import com.dineflow.billing.domain.BillStatus;
import com.dineflow.billing.dto.BillResponse;
import com.dineflow.billing.dto.GenerateBillRequest;
import com.dineflow.billing.dto.VoidBillRequest;
import com.dineflow.billing.service.BillingEngineService;
import com.dineflow.common.api.ApiResponse;
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
@RequestMapping("/api/v1/staff/bills")
public class StaffBillController {

    private final BillingEngineService billingService;

    public StaffBillController(BillingEngineService billingService) {
        this.billingService = billingService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('BILL_VIEW') or hasAuthority('ORDER_VIEW')")
    public ResponseEntity<ApiResponse<List<BillResponse>>> listBills(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @RequestParam(value = "status", required = false) BillStatus status,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "branchId", required = false) UUID branchId) {
        List<BillResponse> bills = billingService.listBills(principal, status, search, branchId);
        return ResponseEntity.ok(ApiResponse.ok(bills, "Bills retrieved"));
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAuthority('BILL_CREATE') or hasAuthority('ORDER_MANAGE')")
    public ResponseEntity<ApiResponse<BillResponse>> generateBill(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @Valid @RequestBody GenerateBillRequest request) {
        BillResponse response = billingService.generateBillForOrders(principal, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Restaurant bill generated successfully"));
    }

    @GetMapping("/{billId}")
    @PreAuthorize("hasAuthority('BILL_VIEW') or hasAuthority('ORDER_VIEW')")
    public ResponseEntity<ApiResponse<BillResponse>> getBill(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @PathVariable("billId") UUID billId) {
        BillResponse response = billingService.getBill(principal, billId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Bill details retrieved"));
    }

    @GetMapping("/by-order/{orderId}")
    @PreAuthorize("hasAuthority('BILL_VIEW') or hasAuthority('ORDER_VIEW')")
    public ResponseEntity<ApiResponse<BillResponse>> getBillByOrder(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @PathVariable("orderId") UUID orderId) {
        BillResponse response = billingService.getBillByOrderId(principal, orderId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Order bill retrieved"));
    }

    @PostMapping("/{billId}/void")
    @PreAuthorize("hasAuthority('BILL_VOID') or hasAuthority('ORDER_MANAGE')")
    public ResponseEntity<ApiResponse<BillResponse>> voidBill(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @PathVariable("billId") UUID billId,
            @Valid @RequestBody VoidBillRequest request) {
        BillResponse response = billingService.voidBill(principal, billId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Bill voided successfully"));
    }
}
