package com.dineflow.invoice.controller;

import com.dineflow.common.api.ApiResponse;
import com.dineflow.invoice.dto.CancelInvoiceRequest;
import com.dineflow.invoice.dto.GenerateInvoiceRequest;
import com.dineflow.invoice.dto.InvoiceResponse;
import com.dineflow.invoice.service.InvoiceService;
import com.dineflow.security.DineFlowPrincipal;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
@RequestMapping("/api/v1/staff/invoices")
public class StaffInvoiceController {

    private final InvoiceService invoiceService;

    public StaffInvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('INVOICE_VIEW') or hasAuthority('ORDER_VIEW')")
    public ResponseEntity<ApiResponse<List<InvoiceResponse>>> listInvoices(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "branchId", required = false) UUID branchId) {
        List<InvoiceResponse> invoices = invoiceService.listInvoices(principal, search, branchId);
        return ResponseEntity.ok(ApiResponse.ok(invoices, "Invoices retrieved"));
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAuthority('INVOICE_GENERATE') or hasAuthority('ORDER_MANAGE')")
    public ResponseEntity<ApiResponse<InvoiceResponse>> generateInvoice(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @Valid @RequestBody GenerateInvoiceRequest request) {
        InvoiceResponse response = invoiceService.generateInvoiceForBill(principal, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Tax invoice generated successfully"));
    }

    @GetMapping("/{invoiceId}")
    @PreAuthorize("hasAuthority('INVOICE_VIEW') or hasAuthority('ORDER_VIEW')")
    public ResponseEntity<ApiResponse<InvoiceResponse>> getInvoice(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @PathVariable("invoiceId") UUID invoiceId) {
        InvoiceResponse response = invoiceService.getInvoice(principal, invoiceId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Invoice details retrieved"));
    }

    @GetMapping("/by-bill/{billId}")
    @PreAuthorize("hasAuthority('INVOICE_VIEW') or hasAuthority('ORDER_VIEW')")
    public ResponseEntity<ApiResponse<InvoiceResponse>> getInvoiceByBill(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @PathVariable("billId") UUID billId) {
        InvoiceResponse response = invoiceService.getInvoiceByBillId(principal, billId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Bill invoice retrieved"));
    }

    @GetMapping("/{invoiceId}/pdf")
    @PreAuthorize("hasAuthority('INVOICE_VIEW') or hasAuthority('ORDER_VIEW')")
    public ResponseEntity<Resource> downloadInvoicePdf(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @PathVariable("invoiceId") UUID invoiceId) {
        Resource pdfResource = invoiceService.getInvoicePdfResource(principal, invoiceId);
        InvoiceResponse invoice = invoiceService.getInvoice(principal, invoiceId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + invoice.invoiceNumber() + ".pdf\"")
                .body(pdfResource);
    }

    @PostMapping("/{invoiceId}/cancel")
    @PreAuthorize("hasAuthority('INVOICE_CANCEL') or hasAuthority('ORDER_MANAGE')")
    public ResponseEntity<ApiResponse<InvoiceResponse>> cancelInvoice(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @PathVariable("invoiceId") UUID invoiceId,
            @Valid @RequestBody CancelInvoiceRequest request) {
        InvoiceResponse response = invoiceService.cancelInvoice(principal, invoiceId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Invoice cancelled successfully"));
    }
}
