package com.dineflow.invoice.controller;

import com.dineflow.common.api.ApiResponse;
import com.dineflow.invoice.dto.InvoiceResponse;
import com.dineflow.invoice.service.InvoiceService;
import com.dineflow.security.DineFlowPrincipal;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customer/invoices")
public class CustomerInvoiceController {

    private final InvoiceService invoiceService;

    public CustomerInvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping("/by-bill/{billId}")
    @PreAuthorize("hasAuthority('CUSTOMER_ORDER_VIEW_OWN')")
    public ResponseEntity<ApiResponse<InvoiceResponse>> getInvoiceByBill(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @PathVariable("billId") UUID billId) {
        InvoiceResponse response = invoiceService.getInvoiceByBillId(principal, billId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Invoice retrieved"));
    }

    @GetMapping("/{invoiceId}/pdf")
    @PreAuthorize("hasAuthority('CUSTOMER_ORDER_VIEW_OWN')")
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
}
