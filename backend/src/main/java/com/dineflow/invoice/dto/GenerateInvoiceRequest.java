package com.dineflow.invoice.dto;

import java.util.UUID;

public record GenerateInvoiceRequest(
        UUID billId,
        String customerName,
        String customerTaxId,
        String customerPhone,
        String paymentMethod,
        String paymentReference,
        String notes
) {}
