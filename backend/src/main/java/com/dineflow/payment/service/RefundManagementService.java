package com.dineflow.payment.service;

import com.dineflow.billing.domain.BillStatus;
import com.dineflow.billing.domain.RestaurantBill;
import com.dineflow.billing.repository.RestaurantBillRepository;
import com.dineflow.common.exception.BusinessException;
import com.dineflow.payment.domain.PaymentAuditRecord;
import com.dineflow.payment.domain.PaymentMethod;
import com.dineflow.payment.domain.PaymentProviderType;
import com.dineflow.payment.domain.PaymentReconciliationLog;
import com.dineflow.payment.domain.PaymentStatus;
import com.dineflow.payment.domain.RefundMethod;
import com.dineflow.payment.domain.RefundStatus;
import com.dineflow.payment.domain.RestaurantPayment;
import com.dineflow.payment.domain.RestaurantRefund;
import com.dineflow.payment.dto.ReconciliationReport;
import com.dineflow.payment.dto.RefundResponse;
import com.dineflow.payment.dto.RequestRefundRequest;
import com.dineflow.payment.provider.PaymentGatewayProvider;
import com.dineflow.payment.repository.PaymentReconciliationLogRepository;
import com.dineflow.payment.repository.RestaurantPaymentRepository;
import com.dineflow.payment.repository.RestaurantRefundRepository;
import com.dineflow.realtime.RealtimeEvent;
import com.dineflow.security.DineFlowPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RefundManagementService {

    private static final Logger log = LoggerFactory.getLogger(RefundManagementService.class);
    private static final BigDecimal AUTO_APPROVE_THRESHOLD = BigDecimal.valueOf(1000.00); // Higher values require independent manager approval

    private final RestaurantRefundRepository refundRepository;
    private final RestaurantPaymentRepository paymentRepository;
    private final RestaurantBillRepository billRepository;
    private final PaymentReconciliationLogRepository reconciliationLogRepository;
    private final PaymentReferenceGenerator referenceGenerator;
    private final Map<PaymentProviderType, PaymentGatewayProvider> providers;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public RefundManagementService(RestaurantRefundRepository refundRepository,
                                   RestaurantPaymentRepository paymentRepository,
                                   RestaurantBillRepository billRepository,
                                   PaymentReconciliationLogRepository reconciliationLogRepository,
                                   PaymentReferenceGenerator referenceGenerator,
                                   List<PaymentGatewayProvider> providerList,
                                   TransactionTemplate transactionTemplate,
                                   @Autowired(required = false) ApplicationEventPublisher eventPublisher) {
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
        this.billRepository = billRepository;
        this.reconciliationLogRepository = reconciliationLogRepository;
        this.referenceGenerator = referenceGenerator;
        this.providers = providerList.stream()
                .collect(Collectors.toMap(PaymentGatewayProvider::getProviderType, p -> p));
        this.transactionTemplate = transactionTemplate;
        this.eventPublisher = eventPublisher;
    }

    public RefundManagementService(RestaurantRefundRepository refundRepository,
                                   RestaurantPaymentRepository paymentRepository,
                                   RestaurantBillRepository billRepository,
                                   PaymentReconciliationLogRepository reconciliationLogRepository,
                                   PaymentReferenceGenerator referenceGenerator,
                                   List<PaymentGatewayProvider> providerList,
                                   TransactionTemplate transactionTemplate) {
        this(refundRepository, paymentRepository, billRepository, reconciliationLogRepository,
                referenceGenerator, providerList, transactionTemplate, null);
    }

    /**
     * Step 1: Request a full or partial refund against an existing SUCCEEDED payment.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RefundResponse requestRefund(DineFlowPrincipal principal, RequestRefundRequest request) {
        UUID tenantId = principal.getTenantId();
        RestaurantPayment payment = paymentRepository.findByIdAndTenantIdForUpdate(request.paymentId(), tenantId)
                .orElseThrow(() -> new BusinessException("Original payment record not found", HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND"));

        if (principal.getBranchId() != null && payment.getBranchId() != null && !payment.getBranchId().equals(principal.getBranchId())) {
            throw new BusinessException("Unauthorized cross-branch refund request", HttpStatus.FORBIDDEN, "CROSS_BRANCH_ACCESS");
        }

        if (payment.getPaymentStatus() != PaymentStatus.SUCCEEDED && payment.getPaymentStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new BusinessException("Refunds can only be requested against settled payments (Status: " + payment.getPaymentStatus() + ")",
                    HttpStatus.CONFLICT, "INVALID_PAYMENT_STATE_FOR_REFUND");
        }

        BigDecimal refundAmt = request.amount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal maxRefundable = calculateRemainingRefundable(payment);

        if (refundAmt.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Refund amount must be strictly positive", HttpStatus.BAD_REQUEST, "INVALID_REFUND_AMOUNT");
        }

        if (refundAmt.compareTo(maxRefundable) > 0) {
            throw new BusinessException(String.format("Requested refund (%s) exceeds remaining refundable balance (%s)",
                    refundAmt, maxRefundable), HttpStatus.BAD_REQUEST, "REFUND_EXCEEDS_BALANCE");
        }

        String refundRef = referenceGenerator.generateUniquePaymentReference(tenantId).replace("PAY", "REF");
        RestaurantRefund refund = new RestaurantRefund(
                tenantId,
                payment.getBranchId(),
                payment,
                payment.getBill(),
                refundRef,
                refundAmt,
                payment.getCurrencyCode(),
                request.refundMethod(),
                request.reason(),
                principal.getUserId()
        );

        // Auto-approve if amount <= threshold AND requester is admin/owner
        boolean canAutoApprove = refundAmt.compareTo(AUTO_APPROVE_THRESHOLD) <= 0
                && (principal.getAuthorities().stream().anyMatch(a -> "REFUND_APPROVE".equals(a.getAuthority()))
                || "HOTEL_OWNER".equalsIgnoreCase(principal.getRoleCode())
                || "HOTEL_ADMIN".equalsIgnoreCase(principal.getRoleCode()));

        if (canAutoApprove) {
            refund.setRefundStatus(RefundStatus.APPROVED);
            refund.setApprovedByUserId(principal.getUserId());
        }

        RestaurantRefund saved = refundRepository.save(refund);
        log.info("Refund request created: Ref {}, Amount: {} {}, Status: {}",
                saved.getRefundReference(), saved.getRefundAmount(), saved.getCurrencyCode(), saved.getRefundStatus());

        return toRefundResponse(saved);
    }

    /**
     * Step 2: Independent Manager/Owner approval for pending high-value refunds.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RefundResponse approveRefund(DineFlowPrincipal principal, UUID refundId) {
        UUID tenantId = principal.getTenantId();
        RestaurantRefund refund = refundRepository.findByIdAndTenantId(refundId, tenantId)
                .orElseThrow(() -> new BusinessException("Refund record not found", HttpStatus.NOT_FOUND, "REFUND_NOT_FOUND"));

        if (principal.getBranchId() != null && refund.getBranchId() != null && !refund.getBranchId().equals(principal.getBranchId())) {
            throw new BusinessException("Unauthorized cross-branch refund action", HttpStatus.FORBIDDEN, "CROSS_BRANCH_ACCESS");
        }

        if (refund.getRefundStatus() != RefundStatus.PENDING_APPROVAL) {
            throw new BusinessException("Refund is not in PENDING_APPROVAL state", HttpStatus.CONFLICT, "INVALID_REFUND_STATE");
        }

        // Separation of duties: User cannot approve their own high-value refund if someone else submitted it
        if (principal.getUserId().equals(refund.getRequestedByUserId()) && refund.getRefundAmount().compareTo(AUTO_APPROVE_THRESHOLD) > 0) {
            throw new BusinessException("Separation of duties violation: Cannot approve your own high-value refund",
                    HttpStatus.FORBIDDEN, "SELF_APPROVAL_PROHIBITED");
        }

        refund.setRefundStatus(RefundStatus.APPROVED);
        refund.setApprovedByUserId(principal.getUserId());

        RestaurantRefund saved = refundRepository.save(refund);
        return toRefundResponse(saved);
    }

    /**
     * Step 3: Execute approved refund via physical cash register or upstream payment gateway.
     * Uses 3-phase transaction boundaries so gateway refund HTTP calls never hold a DB transaction open.
     */
    public RefundResponse executeRefund(DineFlowPrincipal principal, UUID refundId) {
        UUID tenantId = principal.getTenantId();

        record RefundPhaseA(UUID refundId, UUID paymentId, UUID billId, RestaurantPayment payment,
                            BigDecimal amount, String reason, RefundMethod method, PaymentProviderType providerType) {}

        // Phase A: Short DB TX — lock refund row, validate APPROVED state, reserve by moving to INITIATED, and commit
        RefundPhaseA phaseA = transactionTemplate.execute(status -> {
            RestaurantRefund refund = refundRepository.findByIdAndTenantIdForUpdate(refundId, tenantId)
                    .orElseThrow(() -> new BusinessException("Refund record not found", HttpStatus.NOT_FOUND, "REFUND_NOT_FOUND"));

            if (principal.getBranchId() != null && refund.getBranchId() != null && !refund.getBranchId().equals(principal.getBranchId())) {
                throw new BusinessException("Unauthorized cross-branch refund action", HttpStatus.FORBIDDEN, "CROSS_BRANCH_ACCESS");
            }
            if (refund.getRefundStatus() != RefundStatus.APPROVED) {
                throw new BusinessException("Only APPROVED refunds can be executed (Current status: " + refund.getRefundStatus() + ")",
                        HttpStatus.CONFLICT, "REFUND_NOT_APPROVED");
            }

            refund.setRefundStatus(RefundStatus.INITIATED);
            refundRepository.save(refund);

            RestaurantPayment payment = refund.getPayment();
            return new RefundPhaseA(refund.getId(), payment.getId(), refund.getBill().getId(),
                    payment, refund.getRefundAmount(), refund.getReason(), refund.getRefundMethod(), payment.getPaymentProvider());
        });

        // Phase B: External gateway refund call strictly OUTSIDE database transaction
        PaymentGatewayProvider.GatewayRefundResult refundResult = null;
        if (phaseA.method() == RefundMethod.ONLINE_GATEWAY && phaseA.providerType() != PaymentProviderType.CASH_REGISTER) {
            PaymentGatewayProvider gateway = providers.get(phaseA.providerType());
            if (gateway != null) {
                refundResult = gateway.processRefund(phaseA.payment(), phaseA.amount(), phaseA.reason());
            }
        }

        // Phase C: Short DB TX — apply authoritative refund outcome and commit even on failure/ambiguity
        record PhaseCOutcome(RefundResponse response, HttpStatus errorStatus, String errorCode, String errorMessage) {}

        final PaymentGatewayProvider.GatewayRefundResult finalResult = refundResult;
        PhaseCOutcome outcome = transactionTemplate.execute(status -> {
            RestaurantRefund refund = refundRepository.findByIdAndTenantIdForUpdate(phaseA.refundId(), tenantId)
                    .orElseThrow(() -> new BusinessException("Refund record not found", HttpStatus.NOT_FOUND, "REFUND_NOT_FOUND"));
            RestaurantPayment payment = paymentRepository.findByIdAndTenantIdForUpdate(phaseA.paymentId(), tenantId)
                    .orElseThrow(() -> new BusinessException("Original payment record not found", HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND"));
            RestaurantBill bill = billRepository.findByIdAndTenantIdForUpdate(phaseA.billId(), tenantId)
                    .orElseThrow(() -> new BusinessException("Bill not found", HttpStatus.NOT_FOUND, "BILL_NOT_FOUND"));

            if (finalResult != null) {
                if (!finalResult.isSuccess()) {
                    if (finalResult.ambiguous()) {
                        // Keep in INITIATED for reconciliation — commit failure message cleanly (no rollback!)
                        refund.setFailureMessage("Ambiguous gateway response: " + finalResult.errorMessage());
                        refundRepository.save(refund);
                        return new PhaseCOutcome(null, HttpStatus.GATEWAY_TIMEOUT, "GATEWAY_REFUND_AMBIGUOUS",
                                "Gateway refund outcome ambiguous; queued for reconciliation: " + finalResult.errorMessage());
                    }
                    refund.setRefundStatus(RefundStatus.FAILED);
                    refund.setFailureMessage(finalResult.errorMessage());
                    refundRepository.save(refund);
                    return new PhaseCOutcome(null, HttpStatus.BAD_GATEWAY, "GATEWAY_REFUND_FAILED",
                            "Gateway refund execution failed: " + finalResult.errorMessage());
                }
                refund.setProviderRefundId(finalResult.providerRefundId());
            }

            BigDecimal newRefunded = payment.getRefundedAmount().add(refund.getRefundAmount()).setScale(2, RoundingMode.HALF_UP);
            if (newRefunded.compareTo(payment.getPaidAmount()) > 0) {
                refund.setRefundStatus(RefundStatus.FAILED);
                refund.setFailureMessage("Refund amount exceeds remaining paid balance");
                refundRepository.save(refund);
                return new PhaseCOutcome(null, HttpStatus.CONFLICT, "REFUND_EXCEEDS_BALANCE",
                        "Refund amount exceeds remaining paid balance");
            }

            refund.setRefundStatus(RefundStatus.SUCCEEDED);
            refund.setSettledAt(Instant.now());

            payment.setRefundedAmount(newRefunded);
            if (newRefunded.compareTo(payment.getPaidAmount()) >= 0) {
                payment.setPaymentStatus(PaymentStatus.REFUNDED);
            } else {
                payment.setPaymentStatus(PaymentStatus.PARTIALLY_REFUNDED);
            }

            BigDecimal billPaid = bill.getPaidAmount().subtract(refund.getRefundAmount()).setScale(2, RoundingMode.HALF_UP);
            if (billPaid.compareTo(BigDecimal.ZERO) < 0) billPaid = BigDecimal.ZERO;
            bill.setPaidAmount(billPaid);
            bill.setOutstandingBalance(bill.getGrandTotal().subtract(billPaid).setScale(2, RoundingMode.HALF_UP));

            if (billPaid.compareTo(BigDecimal.ZERO) == 0) {
                bill.setStatus(BillStatus.ISSUED);
            } else {
                bill.setStatus(BillStatus.PARTIALLY_PAID);
            }
            billRepository.save(bill);

            PaymentAuditRecord audit = new PaymentAuditRecord(
                    payment,
                    tenantId,
                    payment.getPaymentStatus().name(),
                    refund.getRefundStatus().name(),
                    "REFUND_EXECUTED_" + refund.getRefundMethod().name(),
                    principal.getUserId(),
                    "STAFF",
                    refund.getRefundAmount().negate(),
                    "Refund executed: " + refund.getRefundReference() + " (" + refund.getReason() + ")"
            );
            payment.addAuditRecord(audit);
            paymentRepository.save(payment);

            RestaurantRefund saved = refundRepository.save(refund);
            if (eventPublisher != null) {
                Map<String, Object> refundPayload = Map.of(
                        "refundId", saved.getId().toString(),
                        "billNumber", bill.getBillNumber(),
                        "refundAmount", saved.getRefundAmount(),
                        "currencyCode", saved.getCurrencyCode(),
                        "refundStatus", saved.getRefundStatus().name(),
                        "outstandingBalance", bill.getOutstandingBalance()
                );
                eventPublisher.publishEvent(RealtimeEvent.of(
                        "REFUND_EXECUTED", tenantId, bill.getBranchId(), "payments", saved.getId(), refundPayload));
                if (bill.getCustomerSessionId() != null) {
                    eventPublisher.publishEvent(RealtimeEvent.of(
                            "REFUND_EXECUTED", tenantId, bill.getBranchId(), "session", bill.getCustomerSessionId(), refundPayload));
                }
            }
            log.info("Refund {} executed successfully for payment {} (Amount: {})",
                    saved.getRefundReference(), payment.getPaymentReference(), saved.getRefundAmount());

            return new PhaseCOutcome(toRefundResponse(saved), null, null, null);
        });

        if (outcome.errorStatus() != null) {
            throw new BusinessException(outcome.errorMessage(), outcome.errorStatus(), outcome.errorCode());
        }
        return outcome.response();
    }

    @Transactional(readOnly = true)
    public List<RefundResponse> listRefunds(DineFlowPrincipal principal, RefundStatus status) {
        UUID tenantId = principal.getTenantId();
        UUID branchId = principal.getBranchId();
        List<RestaurantRefund> refunds = branchId != null
                ? refundRepository.findAllByTenantIdAndBranchIdOrderByCreatedAtDesc(tenantId, branchId)
                : refundRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
        return refunds.stream()
                .filter(r -> status == null || r.getRefundStatus() == status)
                .map(this::toRefundResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RefundResponse> getRefundsForPayment(DineFlowPrincipal principal, UUID paymentId) {
        return refundRepository.findAllByPaymentIdAndTenantId(paymentId, principal.getTenantId()).stream()
                .map(this::toRefundResponse)
                .toList();
    }

    /**
     * Reconciles bounded batches of refunds stuck in INITIATED state (e.g., after an ambiguous
     * gateway timeout during executeRefund). External status polls execute strictly outside DB TX.
     */
    public ReconciliationReport reconcileInitiatedRefunds(UUID tenantId, String triggeredBy) {
        record RefundCandidate(UUID refundId, UUID refundTenantId, UUID paymentId, UUID billId,
                               String refundReference, PaymentProviderType providerType,
                               String providerPaymentId, String providerRefundId) {}

        long startMs = System.currentTimeMillis();
        Instant now = Instant.now();

        UUID reconLogId = transactionTemplate.execute(status -> {
            PaymentReconciliationLog reconLog = new PaymentReconciliationLog(tenantId, "REFUND_RECONCILIATION");
            reconLog.setTriggeredBy(triggeredBy != null ? triggeredBy : "SCHEDULER");
            return reconciliationLogRepository.save(reconLog).getId();
        });

        try {
            List<RefundCandidate> candidates = transactionTemplate.execute(status ->
                    refundRepository.findInitiatedRefundsForReconciliation(tenantId, now, PageRequest.of(0, 50))
                            .getContent()
                            .stream()
                            .map(r -> new RefundCandidate(
                                    r.getId(), r.getTenantId(), r.getPayment().getId(), r.getBill().getId(),
                                    r.getRefundReference(), r.getPayment().getPaymentProvider(),
                                    r.getPayment().getProviderPaymentId(), r.getProviderRefundId()
                            ))
                            .toList()
            );

            int scanned = 0;
            int resolved = 0;
            int discrepancies = 0;
            int failedCount = 0;
            int retries = 0;
            StringBuilder summary = new StringBuilder();

            for (RefundCandidate c : candidates) {
                scanned++;
                PaymentGatewayProvider gateway = providers.get(c.providerType());
                if (gateway == null) {
                    continue;
                }

                // External refund status poll runs STRICTLY OUTSIDE any DB transaction
                PaymentGatewayProvider.GatewayRefundResult pollResult;
                try {
                    pollResult = gateway.fetchRefundStatus(c.providerPaymentId(), c.providerRefundId());
                } catch (Exception ex) {
                    pollResult = new PaymentGatewayProvider.GatewayRefundResult(
                            false, c.providerRefundId(), BigDecimal.ZERO, "REFUND_POLL_EXCEPTION", ex.getMessage(), true
                    );
                }

                final PaymentGatewayProvider.GatewayRefundResult finalPoll = pollResult;
                String outcome = transactionTemplate.execute(status -> {
                    RestaurantRefund refund = refundRepository.findByIdAndTenantIdForUpdate(c.refundId(), c.refundTenantId()).orElse(null);
                    if (refund == null || refund.getRefundStatus() != RefundStatus.INITIATED) {
                        return "SKIPPED";
                    }

                    if (finalPoll.isSuccess()) {
                        RestaurantPayment payment = paymentRepository.findByIdAndTenantIdForUpdate(c.paymentId(), c.refundTenantId()).orElseThrow();
                        RestaurantBill bill = billRepository.findByIdAndTenantIdForUpdate(c.billId(), c.refundTenantId()).orElseThrow();

                        refund.setRefundStatus(RefundStatus.SUCCEEDED);
                        refund.setProviderRefundId(finalPoll.providerRefundId() != null ? finalPoll.providerRefundId() : refund.getProviderRefundId());
                        refund.setSettledAt(Instant.now());
                        refund.setManualReviewRequired(false);
                        refund.setNextReconciliationAt(null);

                        BigDecimal newRefunded = payment.getRefundedAmount().add(refund.getRefundAmount()).setScale(2, RoundingMode.HALF_UP);
                        payment.setRefundedAmount(newRefunded);
                        payment.setPaymentStatus(newRefunded.compareTo(payment.getPaidAmount()) >= 0
                                ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);

                        BigDecimal billPaid = bill.getPaidAmount().subtract(refund.getRefundAmount()).setScale(2, RoundingMode.HALF_UP);
                        if (billPaid.compareTo(BigDecimal.ZERO) < 0) billPaid = BigDecimal.ZERO;
                        bill.setPaidAmount(billPaid);
                        bill.setOutstandingBalance(bill.getGrandTotal().subtract(billPaid).setScale(2, RoundingMode.HALF_UP));
                        bill.setStatus(billPaid.compareTo(BigDecimal.ZERO) == 0 ? BillStatus.ISSUED : BillStatus.PARTIALLY_PAID);
                        billRepository.save(bill);

                        payment.addAuditRecord(new PaymentAuditRecord(
                                payment, c.refundTenantId(), payment.getPaymentStatus().name(), RefundStatus.SUCCEEDED.name(),
                                "REFUND_RECONCILED_CONFIRMED", null, "RECONCILER", refund.getRefundAmount().negate(),
                                "Refund reconciled via provider poll: " + refund.getRefundReference()
                        ));
                        paymentRepository.save(payment);
                        refundRepository.save(refund);
                        return "RESOLVED";
                    } else if (finalPoll.ambiguous()) {
                        int nextRetry = refund.getReconciliationRetryCount() + 1;
                        refund.setReconciliationRetryCount(nextRetry);
                        boolean exhausted = ReconciliationRetryPolicy.isExhausted(nextRetry);
                        if (exhausted) {
                            refund.setManualReviewRequired(true);
                            refund.setNextReconciliationAt(null);
                            refund.setFailureMessage("Refund reconciliation retries exhausted (" + nextRetry + "); manual review required");
                        } else {
                            refund.setNextReconciliationAt(ReconciliationRetryPolicy.computeNextAttemptAt(Instant.now(), nextRetry));
                            refund.setFailureMessage("Transient refund poll error (" + finalPoll.errorCode() + "); retry #" + nextRetry);
                        }
                        refundRepository.save(refund);
                        return exhausted ? "MANUAL_REVIEW" : "DISCREPANCY";
                    } else {
                        refund.setRefundStatus(RefundStatus.FAILED);
                        refund.setFailureMessage(finalPoll.errorMessage());
                        refund.setNextReconciliationAt(null);
                        refundRepository.save(refund);
                        return "FAILED";
                    }
                });

                switch (outcome) {
                    case "RESOLVED" -> {
                        resolved++;
                        summary.append("Refund ").append(c.refundReference()).append(" confirmed SUCCEEDED. ");
                    }
                    case "MANUAL_REVIEW" -> {
                        discrepancies++;
                        retries++;
                        summary.append("Refund ").append(c.refundReference()).append(" exhausted retries -> MANUAL_REVIEW. ");
                    }
                    case "DISCREPANCY" -> {
                        discrepancies++;
                        retries++;
                        summary.append("Refund ").append(c.refundReference()).append(" transient poll error; backoff scheduled. ");
                    }
                    case "FAILED" -> {
                        failedCount++;
                        summary.append("Refund ").append(c.refundReference()).append(" confirmed FAILED. ");
                    }
                    default -> { }
                }
            }

            final int fScanned = scanned, fResolved = resolved, fDisc = discrepancies, fRetries = retries;
            final String fSummary = summary.toString();
            final long durationMs = System.currentTimeMillis() - startMs;
            transactionTemplate.executeWithoutResult(status ->
                    reconciliationLogRepository.findById(reconLogId).ifPresent(logRow -> {
                        logRow.setScannedCount(fScanned);
                        logRow.setReconciledCount(fResolved);
                        logRow.setMismatchCount(fDisc);
                        logRow.setRetryCount(fRetries);
                        logRow.setDurationMs(durationMs);
                        logRow.setDiscrepancyDetails(fSummary);
                        logRow.setExecutionStatus("COMPLETED");
                        logRow.setCompletedAt(Instant.now());
                        reconciliationLogRepository.save(logRow);
                    }));

            return new ReconciliationReport(scanned, resolved, discrepancies, failedCount, fSummary);
        } catch (RuntimeException fatal) {
            long durationMs = System.currentTimeMillis() - startMs;
            transactionTemplate.executeWithoutResult(status ->
                    reconciliationLogRepository.findById(reconLogId).ifPresent(logRow -> {
                        logRow.setDurationMs(durationMs);
                        logRow.setExecutionStatus("FAILED");
                        logRow.setDiscrepancyDetails("Refund reconciliation crashed: " + fatal.getMessage());
                        logRow.setCompletedAt(Instant.now());
                        reconciliationLogRepository.save(logRow);
                    }));
            throw fatal;
        }
    }

    private BigDecimal calculateRemainingRefundable(RestaurantPayment payment) {
        List<RestaurantRefund> existingRefunds = refundRepository.findAllByPaymentIdAndTenantId(payment.getId(), payment.getTenantId());
        BigDecimal activeRefundedSum = existingRefunds.stream()
                .filter(r -> r.getRefundStatus() == RefundStatus.SUCCEEDED
                        || r.getRefundStatus() == RefundStatus.APPROVED
                        || r.getRefundStatus() == RefundStatus.PENDING_APPROVAL
                        || r.getRefundStatus() == RefundStatus.INITIATED)
                .map(RestaurantRefund::getRefundAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal remaining = payment.getPaidAmount().subtract(activeRefundedSum).setScale(2, RoundingMode.HALF_UP);
        return remaining.compareTo(BigDecimal.ZERO) > 0 ? remaining : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    public RefundResponse toRefundResponse(RestaurantRefund refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getTenantId(),
                refund.getBranchId(),
                refund.getPayment().getId(),
                refund.getBill().getId(),
                refund.getRefundReference(),
                refund.getRefundAmount(),
                refund.getCurrencyCode(),
                refund.getRefundMethod().name(),
                refund.getRefundStatus().name(),
                refund.getReason(),
                refund.getProviderRefundId(),
                refund.getFailureMessage(),
                refund.getRequestedByUserId(),
                refund.getApprovedByUserId(),
                refund.getSettledAt(),
                refund.getCreatedAt()
        );
    }
}
