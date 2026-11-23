package com.dineflow.invoice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        UUID tenantId,
        UUID branchId,
        UUID billId,
        String invoiceNumber,
        String status,
        String customerName,
        String customerTaxId,
        String customerPhone,
        String restaurantName,
        String taxRegistrationNumber,
        String branchName,
        String branchAddress,
        String currencyCode,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxableAmount,
        BigDecimal taxPercentage,
        BigDecimal taxAmount,
        BigDecimal serviceChargePercentage,
        BigDecimal serviceChargeAmount,
        BigDecimal grandTotal,
        BigDecimal paidAmount,
        String paymentMethod,
        String paymentReference,
        String notes,
        String cancellationReason,
        Instant issuedAt,
        Instant cancelledAt,
        String pdfDownloadUrl
) {}
