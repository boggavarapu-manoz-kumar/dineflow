package com.dineflow.payment.controller;

import com.dineflow.common.api.ApiResponse;
import com.dineflow.payment.domain.PaymentStatus;
import com.dineflow.payment.dto.CashCollectionSummary;
import com.dineflow.payment.dto.InitiatePaymentRequest;
import com.dineflow.payment.dto.PaymentResponse;
import com.dineflow.payment.dto.ReconciliationReport;
import com.dineflow.payment.dto.RecordCashPaymentRequest;
import com.dineflow.payment.dto.ReversePaymentRequest;
import com.dineflow.payment.dto.VerifyPaymentRequest;
import com.dineflow.payment.dto.WebhookProcessingMetrics;
import com.dineflow.payment.service.PaymentProcessingService;
import com.dineflow.payment.service.ReconciliationOperationsService;
import com.dineflow.payment.service.RefundManagementService;
import com.dineflow.security.DineFlowPrincipal;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentProcessingService paymentService;
    private final RefundManagementService refundManagementService;
    private final ReconciliationOperationsService reconciliationOperationsService;

    public PaymentController(PaymentProcessingService paymentService,
                             RefundManagementService refundManagementService,
                             ReconciliationOperationsService reconciliationOperationsService) {
        this.paymentService = paymentService;
        this.refundManagementService = refundManagementService;
        this.reconciliationOperationsService = reconciliationOperationsService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PAYMENT_VIEW') or hasAuthority('BILL_VIEW')")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> listPayments(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @RequestParam(value = "status", required = false) PaymentStatus status,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "branchId", required = false) UUID branchId) {
        List<PaymentResponse> payments = paymentService.listPayments(principal, status, search, branchId);
        return ResponseEntity.ok(ApiResponse.ok(payments, "Payments retrieved"));
    }

    @PostMapping("/initiate")
    @PreAuthorize("hasAuthority('PAYMENT_PROCESS') or hasAuthority('CUSTOMER_ORDER_VIEW_OWN') or hasAuthority('BILL_COLLECT_PAYMENT')")
    public ResponseEntity<ApiResponse<PaymentResponse>> initiatePayment(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Valid @RequestBody InitiatePaymentRequest request) {
        PaymentResponse response = paymentService.initiatePayment(principal, request, idempotencyKeyHeader);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Payment initiated"));
    }

    @PostMapping("/staff/cash")
    @PreAuthorize("hasAuthority('PAYMENT_PROCESS') or hasAuthority('BILL_COLLECT_PAYMENT')")
    public ResponseEntity<ApiResponse<PaymentResponse>> recordStaffCashPayment(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @Valid @RequestBody RecordCashPaymentRequest request) {
        PaymentResponse response = paymentService.recordStaffCashPayment(principal, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Cash payment recorded successfully"));
    }

    @PostMapping("/{paymentId}/reverse")
    @PreAuthorize("hasAuthority('PAYMENT_PROCESS') or hasAuthority('REFUND_APPROVE')")
    public ResponseEntity<ApiResponse<PaymentResponse>> reversePayment(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @PathVariable("paymentId") UUID paymentId,
            @Valid @RequestBody ReversePaymentRequest request) {
        PaymentResponse response = paymentService.reversePayment(principal, paymentId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Payment reversed and bill balance updated"));
    }

    @GetMapping("/staff/cash-summary")
    @PreAuthorize("hasAuthority('PAYMENT_VIEW') or hasAuthority('BILL_VIEW')")
    public ResponseEntity<ApiResponse<CashCollectionSummary>> getCashSummary(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @RequestParam(value = "start", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam(value = "end", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {
        CashCollectionSummary summary = paymentService.getCashCollectionSummary(principal, start, end);
        return ResponseEntity.ok(ApiResponse.ok(summary, "Cash collection summary retrieved"));
    }

    @PostMapping("/{paymentId}/settle")
    @PreAuthorize("hasAuthority('PAYMENT_PROCESS') or hasAuthority('CUSTOMER_ORDER_VIEW_OWN') or hasAuthority('BILL_COLLECT_PAYMENT')")
    public ResponseEntity<ApiResponse<PaymentResponse>> settlePayment(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @PathVariable("paymentId") UUID paymentId,
            @Valid @RequestBody VerifyPaymentRequest request) {
        PaymentResponse response = paymentService.settlePayment(principal, paymentId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Payment settled successfully"));
    }

    @GetMapping("/{paymentId}")
    @PreAuthorize("hasAuthority('PAYMENT_VIEW') or hasAuthority('CUSTOMER_ORDER_VIEW_OWN')")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @PathVariable("paymentId") UUID paymentId) {
        PaymentResponse response = paymentService.getPayment(principal, paymentId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Payment details retrieved"));
    }

    @PostMapping("/webhook/{providerName}")
    public ResponseEntity<ApiResponse<Void>> handleProviderWebhook(
            @PathVariable("providerName") String providerName,
            @RequestBody String rawPayload,
            @RequestHeader Map<String, String> headers) {
        paymentService.processProviderWebhook(providerName, rawPayload, headers);
        return ResponseEntity.ok(ApiResponse.ok(null, "Webhook processed successfully"));
    }

    @PostMapping("/reconciliation/run")
    @PreAuthorize("hasAuthority('REPORT_FINANCIAL_VIEW') or hasAuthority('PAYMENT_PROCESS')")
    public ResponseEntity<ApiResponse<ReconciliationReport>> runReconciliation(
            @AuthenticationPrincipal DineFlowPrincipal principal) {
        ReconciliationReport report = paymentService.reconcilePendingPayments(
                principal.getTenantId(), "MANUAL_STAFF:" + principal.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(report, "Payment reconciliation executed"));
    }

    @PostMapping("/reconciliation/refunds/run")
    @PreAuthorize("hasAuthority('REPORT_FINANCIAL_VIEW') or hasAuthority('REFUND_APPROVE')")
    public ResponseEntity<ApiResponse<ReconciliationReport>> runRefundReconciliation(
            @AuthenticationPrincipal DineFlowPrincipal principal) {
        ReconciliationReport report = refundManagementService.reconcileInitiatedRefunds(
                principal.getTenantId(), "MANUAL_STAFF:" + principal.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(report, "Refund reconciliation executed"));
    }

    @GetMapping("/reconciliation/history")
    @PreAuthorize("hasAuthority('REPORT_FINANCIAL_VIEW') or hasAuthority('PAYMENT_VIEW')")
    public ResponseEntity<ApiResponse<List<ReconciliationOperationsService.JobExecutionView>>> getReconciliationHistory(
            @AuthenticationPrincipal DineFlowPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                reconciliationOperationsService.getRecentExecutions(principal.getTenantId()),
                "Reconciliation history retrieved"
        ));
    }

    @GetMapping("/reconciliation/locks")
    @PreAuthorize("hasAuthority('PLATFORM_MANAGE')")
    public ResponseEntity<ApiResponse<List<ReconciliationOperationsService.SchedulerLockStatus>>> getSchedulerLocks() {
        return ResponseEntity.ok(ApiResponse.ok(
                reconciliationOperationsService.getSchedulerLocks(),
                "Scheduler lock statuses retrieved"
        ));
    }

    @GetMapping("/metrics/webhooks")
    @PreAuthorize("hasAuthority('REPORT_FINANCIAL_VIEW') or hasAuthority('PAYMENT_VIEW')")
    public ResponseEntity<ApiResponse<WebhookProcessingMetrics>> getWebhookMetrics(
            @AuthenticationPrincipal DineFlowPrincipal principal) {
        WebhookProcessingMetrics metrics = paymentService.getWebhookMetrics(principal.getTenantId());
        return ResponseEntity.ok(ApiResponse.ok(metrics, "Webhook metrics retrieved"));
    }
}
