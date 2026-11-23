package com.dineflow.invoice.service;

import com.dineflow.billing.domain.BillStatus;
import com.dineflow.billing.domain.RestaurantBill;
import com.dineflow.billing.repository.RestaurantBillRepository;
import com.dineflow.common.exception.BusinessException;
import com.dineflow.invoice.domain.FinancialDocumentRecord;
import com.dineflow.invoice.domain.InvoiceStatus;
import com.dineflow.invoice.domain.RestaurantInvoice;
import com.dineflow.invoice.dto.CancelInvoiceRequest;
import com.dineflow.invoice.dto.GenerateInvoiceRequest;
import com.dineflow.invoice.dto.InvoiceResponse;
import com.dineflow.invoice.repository.FinancialDocumentRecordRepository;
import com.dineflow.invoice.repository.RestaurantInvoiceRepository;
import com.dineflow.security.DineFlowPrincipal;
import com.dineflow.tenant.domain.HotelProfile;
import com.dineflow.tenant.repository.HotelProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private final RestaurantInvoiceRepository invoiceRepository;
    private final RestaurantBillRepository billRepository;
    private final FinancialDocumentRecordRepository documentRecordRepository;
    private final HotelProfileRepository hotelProfileRepository;
    private final InvoiceNumberGenerator invoiceNumberGenerator;
    private final InvoicePdfGenerator pdfGenerator;
    private final DocumentStorageService storageService;

    public InvoiceService(RestaurantInvoiceRepository invoiceRepository,
                          RestaurantBillRepository billRepository,
                          FinancialDocumentRecordRepository documentRecordRepository,
                          HotelProfileRepository hotelProfileRepository,
                          InvoiceNumberGenerator invoiceNumberGenerator,
                          InvoicePdfGenerator pdfGenerator,
                          DocumentStorageService storageService) {
        this.invoiceRepository = invoiceRepository;
        this.billRepository = billRepository;
        this.documentRecordRepository = documentRecordRepository;
        this.hotelProfileRepository = hotelProfileRepository;
        this.invoiceNumberGenerator = invoiceNumberGenerator;
        this.pdfGenerator = pdfGenerator;
        this.storageService = storageService;
    }

    /**
     * Generates an official, immutable fiscal invoice from a confirmed or settled bill.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InvoiceResponse generateInvoiceForBill(DineFlowPrincipal principal, GenerateInvoiceRequest request) {
        UUID tenantId = principal.getTenantId();
        if (request == null || request.billId() == null) {
            throw new BusinessException("Bill ID is required to generate an invoice", HttpStatus.BAD_REQUEST, "BILL_ID_REQUIRED");
        }

        RestaurantBill bill = billRepository.findByIdAndTenantId(request.billId(), tenantId)
                .orElseThrow(() -> new BusinessException("Bill not found", HttpStatus.NOT_FOUND, "BILL_NOT_FOUND"));

        if (principal.getBranchId() != null && bill.getBranchId() != null && !bill.getBranchId().equals(principal.getBranchId())) {
            throw new BusinessException("Unauthorized cross-branch bill access", HttpStatus.FORBIDDEN, "CROSS_BRANCH_ACCESS");
        }

        if (bill.getStatus() == BillStatus.VOIDED) {
            throw new BusinessException("Cannot generate an invoice for a voided bill", HttpStatus.BAD_REQUEST, "CANNOT_INVOICE_VOIDED_BILL");
        }

        // Check if invoice already issued for this bill
        Optional<RestaurantInvoice> existingInvoice = invoiceRepository.findByBillIdAndTenantId(bill.getId(), tenantId);
        if (existingInvoice.isPresent() && existingInvoice.get().getStatus() != InvoiceStatus.CANCELLED) {
            return toInvoiceResponse(existingInvoice.get());
        }

        // Legal tax registration ID is optional; never substitute contact email into fiscal tax ID
        String taxRegNo = null;

        // 1. Generate Unique Invoice Number
        String invoiceNumber = invoiceNumberGenerator.generateUniqueInvoiceNumber(tenantId);
        RestaurantInvoice invoice = new RestaurantInvoice(tenantId, bill.getBranchId(), bill, invoiceNumber, bill.getRestaurantNameSnapshot());
        invoice.setTaxRegistrationNumber(taxRegNo);
        invoice.setBranchNameSnapshot(bill.getBranchNameSnapshot());
        invoice.setBranchAddressSnapshot(bill.getBranchAddressSnapshot());
        invoice.setCurrencyCode(bill.getCurrencyCode());
        invoice.setSubtotal(bill.getSubtotal());
        invoice.setDiscountAmount(bill.getDiscountAmount());
        invoice.setTaxableAmount(bill.getSubtotal().subtract(bill.getDiscountAmount()).add(bill.getServiceChargeAmount()));
        invoice.setTaxPercentage(bill.getTaxPercentage());
        invoice.setTaxAmount(bill.getTaxAmount());
        invoice.setServiceChargePercentage(bill.getServiceChargePercentage());
        invoice.setServiceChargeAmount(bill.getServiceChargeAmount());
        invoice.setGrandTotal(bill.getGrandTotal());
        invoice.setPaidAmount(bill.getPaidAmount().signum() > 0 ? bill.getPaidAmount() : bill.getGrandTotal());
        invoice.setPaymentMethod(request.paymentMethod() != null ? request.paymentMethod() : "PAID_COUNTER");
        invoice.setPaymentReference(request.paymentReference());
        invoice.setCustomerName(request.customerName());
        invoice.setCustomerTaxId(request.customerTaxId());
        invoice.setCustomerPhone(request.customerPhone());
        invoice.setNotes(request.notes());
        invoice.setIssuedByUserId(principal.getUserId());
        invoice.setIssuedAt(Instant.now());

        RestaurantInvoice savedInvoice = invoiceRepository.save(invoice);

        // 2. Render & Store PDF binary
        byte[] pdfBytes = pdfGenerator.generateInvoicePdf(savedInvoice, bill);
        String fileName = invoiceNumber + ".pdf";
        DocumentStorageService.StoredDocumentResult storedDoc = storageService.storeDocument(tenantId, savedInvoice.getId(), fileName, pdfBytes);

        // 3. Persist FinancialDocumentRecord metadata
        FinancialDocumentRecord docRecord = new FinancialDocumentRecord(
                tenantId,
                "INVOICE_PDF",
                savedInvoice.getId(),
                storageService.getStorageBackendName(),
                storedDoc.storageKey(),
                storedDoc.fileName(),
                "application/pdf",
                storedDoc.fileSizeBytes(),
                storedDoc.checksumSha256()
        );
        documentRecordRepository.save(docRecord);

        log.info("Invoice generated and stored: InvoiceId: {}, Number: {}, Tenant: {}, File: {}",
                savedInvoice.getId(), savedInvoice.getInvoiceNumber(), tenantId, storedDoc.storageKey());

        return toInvoiceResponse(savedInvoice);
    }

    /**
     * Loads the raw PDF resource for download with tenant scoping and path validation.
     */
    @Transactional(readOnly = true)
    public Resource getInvoicePdfResource(DineFlowPrincipal principal, UUID invoiceId) {
        UUID tenantId = principal.getTenantId();
        RestaurantInvoice invoice = invoiceRepository.findByIdAndTenantId(invoiceId, tenantId)
                .orElseThrow(() -> new BusinessException("Invoice not found", HttpStatus.NOT_FOUND, "INVOICE_NOT_FOUND"));

        if (principal.getBranchId() != null && invoice.getBranchId() != null && !invoice.getBranchId().equals(principal.getBranchId())) {
            throw new BusinessException("Unauthorized cross-branch document download", HttpStatus.FORBIDDEN, "CROSS_BRANCH_ACCESS");
        }
        if ("GUEST".equalsIgnoreCase(principal.getType())
                && (invoice.getBill().getCustomerSessionId() == null || !invoice.getBill().getCustomerSessionId().equals(principal.getUserId()))) {
            throw new BusinessException("Unauthorized cross-guest document download", HttpStatus.FORBIDDEN, "UNAUTHORIZED_INVOICE_ACCESS");
        }

        FinancialDocumentRecord doc = documentRecordRepository.findByEntityIdAndDocumentTypeAndTenantId(invoiceId, "INVOICE_PDF", tenantId)
                .orElseThrow(() -> new BusinessException("Document binary not found for invoice", HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND"));

        return storageService.loadDocumentAsResource(tenantId, doc.getStorageKey(), doc.getChecksumSha256())
                .orElseThrow(() -> new BusinessException("File resource could not be read from storage", HttpStatus.INTERNAL_SERVER_ERROR, "FILE_READ_ERROR"));
    }

    /**
     * Retrieves invoice details by ID.
     */
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoice(DineFlowPrincipal principal, UUID invoiceId) {
        UUID tenantId = principal.getTenantId();
        RestaurantInvoice invoice = invoiceRepository.findByIdAndTenantId(invoiceId, tenantId)
                .orElseThrow(() -> new BusinessException("Invoice not found", HttpStatus.NOT_FOUND, "INVOICE_NOT_FOUND"));

        if (principal.getBranchId() != null && invoice.getBranchId() != null && !invoice.getBranchId().equals(principal.getBranchId())) {
            throw new BusinessException("Unauthorized cross-branch invoice access", HttpStatus.FORBIDDEN, "CROSS_BRANCH_ACCESS");
        }
        if ("GUEST".equalsIgnoreCase(principal.getType())
                && (invoice.getBill().getCustomerSessionId() == null || !invoice.getBill().getCustomerSessionId().equals(principal.getUserId()))) {
            throw new BusinessException("Unauthorized cross-guest invoice access", HttpStatus.FORBIDDEN, "UNAUTHORIZED_INVOICE_ACCESS");
        }

        return toInvoiceResponse(invoice);
    }

    /**
     * Lists tenant/branch invoices with optional invoice number or customer search.
     */
    @Transactional(readOnly = true)
    public List<InvoiceResponse> listInvoices(DineFlowPrincipal principal, String search, UUID requestedBranchId) {
        UUID tenantId = principal.getTenantId();
        UUID effectiveBranch = principal.getBranchId() != null ? principal.getBranchId() : requestedBranchId;
        if (principal.getBranchId() != null && requestedBranchId != null && !principal.getBranchId().equals(requestedBranchId)) {
            throw new BusinessException("Unauthorized cross-branch invoice access", HttpStatus.FORBIDDEN, "CROSS_BRANCH_ACCESS");
        }

        List<RestaurantInvoice> invoices = effectiveBranch != null
                ? invoiceRepository.findAllByTenantIdAndBranchIdOrderByIssuedAtDesc(tenantId, effectiveBranch)
                : invoiceRepository.findAllByTenantIdOrderByIssuedAtDesc(tenantId);

        String q = search != null ? search.trim().toLowerCase() : null;
        return invoices.stream()
                .filter(inv -> q == null || q.isEmpty()
                        || inv.getInvoiceNumber().toLowerCase().contains(q)
                        || (inv.getCustomerName() != null && inv.getCustomerName().toLowerCase().contains(q)))
                .map(this::toInvoiceResponse)
                .toList();
    }

    /**
     * Retrieves invoice issued for a specific bill.
     */
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceByBillId(DineFlowPrincipal principal, UUID billId) {
        UUID tenantId = principal.getTenantId();
        RestaurantInvoice invoice = invoiceRepository.findByBillIdAndTenantId(billId, tenantId)
                .orElseThrow(() -> new BusinessException("No invoice found for bill", HttpStatus.NOT_FOUND, "INVOICE_NOT_FOUND_FOR_BILL"));

        if (principal.getBranchId() != null && invoice.getBranchId() != null && !invoice.getBranchId().equals(principal.getBranchId())) {
            throw new BusinessException("Unauthorized cross-branch invoice access", HttpStatus.FORBIDDEN, "CROSS_BRANCH_ACCESS");
        }
        if ("GUEST".equalsIgnoreCase(principal.getType())
                && (invoice.getBill().getCustomerSessionId() == null || !invoice.getBill().getCustomerSessionId().equals(principal.getUserId()))) {
            throw new BusinessException("Unauthorized cross-guest invoice access", HttpStatus.FORBIDDEN, "UNAUTHORIZED_INVOICE_ACCESS");
        }

        return toInvoiceResponse(invoice);
    }

    /**
     * Cancels an issued invoice (creates formal audit cancellation / credit note prerequisite).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InvoiceResponse cancelInvoice(DineFlowPrincipal principal, UUID invoiceId, CancelInvoiceRequest request) {
        UUID tenantId = principal.getTenantId();
        RestaurantInvoice invoice = invoiceRepository.findByIdAndTenantId(invoiceId, tenantId)
                .orElseThrow(() -> new BusinessException("Invoice not found", HttpStatus.NOT_FOUND, "INVOICE_NOT_FOUND"));

        if (principal.getBranchId() != null && invoice.getBranchId() != null && !invoice.getBranchId().equals(principal.getBranchId())) {
            throw new BusinessException("Unauthorized cross-branch invoice access", HttpStatus.FORBIDDEN, "CROSS_BRANCH_ACCESS");
        }
        if ("GUEST".equalsIgnoreCase(principal.getType())
                && (invoice.getBill().getCustomerSessionId() == null || !invoice.getBill().getCustomerSessionId().equals(principal.getUserId()))) {
            throw new BusinessException("Unauthorized cross-guest invoice access", HttpStatus.FORBIDDEN, "UNAUTHORIZED_INVOICE_ACCESS");
        }

        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new BusinessException("Invoice is already cancelled", HttpStatus.BAD_REQUEST, "INVOICE_ALREADY_CANCELLED");
        }

        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoice.setCancellationReason(request.reason().trim());
        invoice.setCancelledByUserId(principal.getUserId());
        invoice.setCancelledAt(Instant.now());

        RestaurantInvoice saved = invoiceRepository.save(invoice);
        log.info("Invoice cancelled: Number: {}, User: {}, Reason: {}", saved.getInvoiceNumber(), principal.getUserId(), request.reason());
        return toInvoiceResponse(saved);
    }

    public InvoiceResponse toInvoiceResponse(RestaurantInvoice invoice) {
        String pdfUrl = "/api/v1/staff/invoices/" + invoice.getId() + "/pdf";
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getTenantId(),
                invoice.getBranchId(),
                invoice.getBill().getId(),
                invoice.getInvoiceNumber(),
                invoice.getStatus().name(),
                invoice.getCustomerName(),
                invoice.getCustomerTaxId(),
                invoice.getCustomerPhone(),
                invoice.getRestaurantNameSnapshot(),
                invoice.getTaxRegistrationNumber(),
                invoice.getBranchNameSnapshot(),
                invoice.getBranchAddressSnapshot(),
                invoice.getCurrencyCode(),
                invoice.getSubtotal(),
                invoice.getDiscountAmount(),
                invoice.getTaxableAmount(),
                invoice.getTaxPercentage(),
                invoice.getTaxAmount(),
                invoice.getServiceChargePercentage(),
                invoice.getServiceChargeAmount(),
                invoice.getGrandTotal(),
                invoice.getPaidAmount(),
                invoice.getPaymentMethod(),
                invoice.getPaymentReference(),
                invoice.getNotes(),
                invoice.getCancellationReason(),
                invoice.getIssuedAt(),
                invoice.getCancelledAt(),
                pdfUrl
        );
    }
}
