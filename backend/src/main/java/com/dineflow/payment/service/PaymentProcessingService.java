package com.dineflow.payment.service;

import com.dineflow.billing.domain.BillStatus;
import com.dineflow.billing.domain.RestaurantBill;
import com.dineflow.billing.repository.RestaurantBillRepository;
import com.dineflow.common.exception.BusinessException;
import com.dineflow.payment.domain.PaymentAttempt;
import com.dineflow.payment.domain.PaymentAuditRecord;
import com.dineflow.payment.domain.PaymentMethod;
import com.dineflow.payment.domain.PaymentProviderType;
import com.dineflow.payment.domain.PaymentReconciliationLog;
import com.dineflow.payment.domain.PaymentStateMachine;
import com.dineflow.payment.domain.PaymentStatus;
import com.dineflow.payment.domain.PaymentWebhookEvent;
import com.dineflow.payment.domain.RestaurantPayment;
import com.dineflow.payment.dto.CashCollectionSummary;
import com.dineflow.payment.dto.InitiatePaymentRequest;
import com.dineflow.payment.dto.PaymentResponse;
import com.dineflow.payment.dto.ReconciliationReport;
import com.dineflow.payment.dto.RecordCashPaymentRequest;
import com.dineflow.payment.dto.ReversePaymentRequest;
import com.dineflow.payment.dto.VerifyPaymentRequest;
import com.dineflow.payment.dto.WebhookProcessingMetrics;
import com.dineflow.payment.provider.PaymentGatewayProvider;
import com.dineflow.payment.repository.PaymentAttemptRepository;
import com.dineflow.payment.repository.PaymentReconciliationLogRepository;
import com.dineflow.payment.repository.PaymentWebhookEventRepository;
import com.dineflow.payment.repository.RestaurantPaymentRepository;
import com.dineflow.realtime.RealtimeEvent;
import com.dineflow.security.DineFlowPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PaymentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessingService.class);

    private final RestaurantPaymentRepository paymentRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final RestaurantBillRepository billRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final PaymentReconciliationLogRepository reconciliationLogRepository;
    private final PaymentReferenceGenerator referenceGenerator;
    private final Map<PaymentProviderType, PaymentGatewayProvider> providers;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentProcessingService(RestaurantPaymentRepository paymentRepository,
                                    PaymentAttemptRepository attemptRepository,
                                    RestaurantBillRepository billRepository,
                                    PaymentWebhookEventRepository webhookEventRepository,
                                    PaymentReconciliationLogRepository reconciliationLogRepository,
                                    PaymentReferenceGenerator referenceGenerator,
                                    List<PaymentGatewayProvider> providerList,
                                    TransactionTemplate transactionTemplate,
                                    ApplicationEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.attemptRepository = attemptRepository;
        this.billRepository = billRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.reconciliationLogRepository = reconciliationLogRepository;
        this.referenceGenerator = referenceGenerator;
        this.providers = providerList.stream()
                .collect(Collectors.toMap(PaymentGatewayProvider::getProviderType, p -> p));
        this.transactionTemplate = transactionTemplate;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Staff Action: Direct In-Person Cash / Pay-at-Counter Recording.
     * Validates amount, partial payment bounds, authoritatively adjusts bill, and creates audit log.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentResponse recordStaffCashPayment(DineFlowPrincipal principal, RecordCashPaymentRequest request) {
        if (!"STAFF".equalsIgnoreCase(principal.getType())) {
            throw new BusinessException("Only authorized restaurant staff can record physical cash receipts",
                    HttpStatus.FORBIDDEN, "STAFF_AUTH_REQUIRED");
        }

        UUID tenantId = principal.getTenantId();
        RestaurantBill bill = billRepository.findByIdAndTenantIdForUpdate(request.billId(), tenantId)
                .orElseThrow(() -> new BusinessException("Bill not found", HttpStatus.NOT_FOUND, "BILL_NOT_FOUND"));

        if (principal.getBranchId() != null && bill.getBranchId() != null && !bill.getBranchId().equals(principal.getBranchId())) {
            throw new BusinessException("Unauthorized cross-branch payment recording", HttpStatus.FORBIDDEN, "CROSS_BRANCH_ACCESS");
        }

        if (bill.getStatus() == BillStatus.PAID) {
            throw new BusinessException("Bill is already fully paid", HttpStatus.CONFLICT, "BILL_ALREADY_PAID");
        }

        if (bill.getStatus() == BillStatus.VOIDED) {
            throw new BusinessException("Cannot accept payment for a voided bill", HttpStatus.BAD_REQUEST, "BILL_VOIDED");
        }

        BigDecimal amountReceived = request.amountReceived().setScale(2, RoundingMode.HALF_UP);
        if (amountReceived.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Payment amount must be greater than zero", HttpStatus.BAD_REQUEST, "INVALID_AMOUNT");
        }

        if (amountReceived.compareTo(bill.getOutstandingBalance()) > 0) {
            throw new BusinessException(String.format("Payment amount (%s) exceeds outstanding bill balance (%s)",
                    amountReceived, bill.getOutstandingBalance()), HttpStatus.BAD_REQUEST, "AMOUNT_EXCEEDS_BALANCE");
        }

        String paymentRef = referenceGenerator.generateUniquePaymentReference(tenantId);
        RestaurantPayment payment = new RestaurantPayment(
                tenantId,
                bill.getBranchId(),
                bill,
                paymentRef,
                PaymentMethod.CASH,
                amountReceived,
                bill.getCurrencyCode(),
                PaymentProviderType.CASH_REGISTER
        );

        payment.setPaidAmount(amountReceived);
        payment.setPaymentStatus(PaymentStatus.SUCCEEDED);
        payment.setNotes(request.notes());
        payment.setCollectedByUserId(principal.getUserId());
        payment.setSettledAt(Instant.now());

        // Create successful attempt record
        PaymentAttempt attempt = new PaymentAttempt(
                payment,
                tenantId,
                1,
                paymentRef + "-CASH",
                "CASH_REGISTER",
                amountReceived,
                bill.getCurrencyCode()
        );
        attempt.setStatus("SUCCEEDED");
        attempt.setProviderTransactionId("CASH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        payment.addAttempt(attempt);

        // Audit Trail
        PaymentAuditRecord audit = new PaymentAuditRecord(
                payment,
                tenantId,
                null,
                PaymentStatus.SUCCEEDED.name(),
                "CASH_COLLECTED_STAFF",
                principal.getUserId(),
                "STAFF",
                amountReceived,
                String.format("Staff collected cash %s %s for bill %s", amountReceived, bill.getCurrencyCode(), bill.getBillNumber())
        );
        payment.addAuditRecord(audit);

        // Update Bill Ledger Balances
        applyPaymentSettlementToBill(bill, amountReceived);

        RestaurantPayment saved = paymentRepository.save(payment);
        log.info("Staff {} recorded cash payment {} for bill {} (Amount: {})",
                principal.getUserId(), saved.getPaymentReference(), bill.getBillNumber(), amountReceived);

        return toPaymentResponse(saved, null, null, null);
    }

    /**
     * Staff Action: Audited Payment Reversal.
     * Corrects erroneous confirmed payments without editing original transaction values directly.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentResponse reversePayment(DineFlowPrincipal principal, UUID paymentId, ReversePaymentRequest request) {
        if (!"STAFF".equalsIgnoreCase(principal.getType())) {
            throw new BusinessException("Only authorized staff can perform payment reversals",
                    HttpStatus.FORBIDDEN, "STAFF_AUTH_REQUIRED");
        }

        UUID tenantId = principal.getTenantId();
        RestaurantPayment payment = paymentRepository.findByIdAndTenantIdForUpdate(paymentId, tenantId)
                .orElseThrow(() -> new BusinessException("Payment not found", HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND"));

        if (principal.getBranchId() != null && payment.getBranchId() != null && !payment.getBranchId().equals(principal.getBranchId())) {
            throw new BusinessException("Unauthorized cross-branch payment reversal", HttpStatus.FORBIDDEN, "CROSS_BRANCH_ACCESS");
        }

        if (payment.getPaymentStatus() != PaymentStatus.SUCCEEDED) {
            throw new BusinessException("Only SUCCEEDED payments can be reversed", HttpStatus.CONFLICT, "INVALID_REVERSAL_STATE");
        }
        if (payment.getRefundedAmount() != null && payment.getRefundedAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException("Cannot reverse a payment that already has refunds recorded", HttpStatus.CONFLICT, "PAYMENT_ALREADY_REFUNDED");
        }

        RestaurantBill bill = billRepository.findByIdAndTenantIdForUpdate(payment.getBill().getId(), tenantId)
                .orElseThrow(() -> new BusinessException("Bill not found", HttpStatus.NOT_FOUND, "BILL_NOT_FOUND"));
        String prevStatus = payment.getPaymentStatus().name();

        // 1. Mark Payment Reversal
        payment.setPaymentStatus(PaymentStatus.REFUNDED);
        payment.setRefundedAmount(payment.getPaidAmount());
        payment.setNotes((payment.getNotes() != null ? payment.getNotes() + " | " : "") + "REVERSED: " + request.reason());

        // 2. Adjust Bill Balance in Reverse
        BigDecimal reversedAmount = payment.getPaidAmount();
        BigDecimal newPaidAmount = bill.getPaidAmount().subtract(reversedAmount).setScale(2, RoundingMode.HALF_UP);
        if (newPaidAmount.compareTo(BigDecimal.ZERO) < 0) {
            newPaidAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal newOutstanding = bill.getGrandTotal().subtract(newPaidAmount).setScale(2, RoundingMode.HALF_UP);
        bill.setPaidAmount(newPaidAmount);
        bill.setOutstandingBalance(newOutstanding);

        if (newPaidAmount.compareTo(BigDecimal.ZERO) == 0) {
            bill.setStatus(BillStatus.ISSUED);
        } else {
            bill.setStatus(BillStatus.PARTIALLY_PAID);
        }
        billRepository.save(bill);

        // 3. Record Reversal Audit Log
        PaymentAuditRecord audit = new PaymentAuditRecord(
                payment,
                tenantId,
                prevStatus,
                PaymentStatus.REFUNDED.name(),
                "PAYMENT_REVERSED_STAFF",
                principal.getUserId(),
                "STAFF",
                reversedAmount.negate(),
                "Reversal Reason: " + request.reason()
        );
        payment.addAuditRecord(audit);

        RestaurantPayment saved = paymentRepository.save(payment);
        log.warn("Staff {} reversed payment {} (Reversed: {}) - Reason: {}",
                principal.getUserId(), saved.getPaymentReference(), reversedAmount, request.reason());

        return toPaymentResponse(saved, null, null, null);
    }

    /**
     * Branch Cash Collection Summary for Shift/Day Reconciliation.
     */
    @Transactional(readOnly = true)
    public CashCollectionSummary getCashCollectionSummary(DineFlowPrincipal principal, Instant start, Instant end) {
        UUID tenantId = principal.getTenantId();
        UUID branchId = principal.getBranchId();

        Instant periodStart = start != null ? start : Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant periodEnd = end != null ? end : Instant.now();

        List<RestaurantPayment> branchPayments = (branchId != null)
                ? paymentRepository.findAllByTenantIdAndBranchIdAndCreatedAtBetweenOrderByCreatedAtDesc(tenantId, branchId, periodStart, periodEnd)
                : paymentRepository.findAllByTenantIdAndCreatedAtBetweenOrderByCreatedAtDesc(tenantId, periodStart, periodEnd);

        List<RestaurantPayment> cashPayments = branchPayments.stream()
                .filter(p -> p.getPaymentMethod() == PaymentMethod.CASH || p.getPaymentMethod() == PaymentMethod.PAY_AT_COUNTER)
                .toList();

        BigDecimal totalReceived = BigDecimal.ZERO;
        BigDecimal totalReversed = BigDecimal.ZERO;
        int txnCount = 0;

        for (RestaurantPayment p : cashPayments) {
            if (p.getPaymentStatus() == PaymentStatus.SUCCEEDED) {
                totalReceived = totalReceived.add(p.getPaidAmount());
                txnCount++;
            } else if (p.getPaymentStatus() == PaymentStatus.REFUNDED) {
                totalReceived = totalReceived.add(p.getPaidAmount()); // was originally received
                totalReversed = totalReversed.add(p.getRefundedAmount());
                txnCount++;
            }
        }

        BigDecimal netCash = totalReceived.subtract(totalReversed).setScale(2, RoundingMode.HALF_UP);

        String currencyCode = cashPayments.stream()
                .map(RestaurantPayment::getCurrencyCode)
                .filter(c -> c != null && !c.isBlank())
                .findFirst()
                .orElse("INR");

        return new CashCollectionSummary(
                tenantId,
                branchId,
                principal.getUserId(),
                "Authorized Staff",
                periodStart,
                periodEnd,
                txnCount,
                totalReceived.setScale(2, RoundingMode.HALF_UP),
                totalReversed.setScale(2, RoundingMode.HALF_UP),
                netCash,
                currencyCode
        );
    }

    /**
     * Backward-compatible overload without explicit HTTP Idempotency-Key header.
     */
    public PaymentResponse initiatePayment(DineFlowPrincipal principal, InitiatePaymentRequest request) {
        return initiatePayment(principal, request, null);
    }

    /**
     * Initiates a payment against an active bill using 3 strictly separated phases:
     * Phase A (short DB TX): Lock bill, validate ownership/balance/currency, check idempotency key + payload hash,
     *                        persist INITIATED payment + attempt + audit record, and commit.
     * Phase B (NO DB TX):    Perform external gateway HTTP order creation (never holds HikariCP connection).
     * Phase C (short DB TX): Lock payment, protect against more-advanced concurrent webhook state,
     *                        persist provider order ID, PENDING_CONFIRMATION (on ambiguous timeout/5xx), or FAILED.
     */
    public PaymentResponse initiatePayment(DineFlowPrincipal principal, InitiatePaymentRequest request, String idempotencyKeyHeader) {
        UUID tenantId = principal.getTenantId();
        String clientIdempotencyKey = resolveIdempotencyKey(idempotencyKeyHeader, request.idempotencyKey());

        record PhaseAResult(
                PaymentResponse replayResponse,
                UUID paymentId,
                UUID attemptId,
                String paymentReference,
                RestaurantBill bill,
                RestaurantPayment payment,
                PaymentAttempt attempt,
                PaymentProviderType providerType
        ) {}

        // Phase A: Short DB Transaction — validate, enforce idempotency, persist intent & commit
        PhaseAResult phaseA;
        try {
            phaseA = transactionTemplate.execute(status -> {
                RestaurantBill bill = billRepository.findByIdAndTenantIdForUpdate(request.billId(), tenantId)
                        .orElseThrow(() -> new BusinessException("Bill not found", HttpStatus.NOT_FOUND, "BILL_NOT_FOUND"));

                if (principal.getBranchId() != null && bill.getBranchId() != null && !bill.getBranchId().equals(principal.getBranchId())) {
                    throw new BusinessException("Unauthorized cross-branch payment access", HttpStatus.FORBIDDEN, "CROSS_BRANCH_ACCESS");
                }
                if ("GUEST".equalsIgnoreCase(principal.getType())
                        && (bill.getCustomerSessionId() == null || !bill.getCustomerSessionId().equals(principal.getUserId()))) {
                    throw new BusinessException("Unauthorized cross-guest payment initiation", HttpStatus.FORBIDDEN, "UNAUTHORIZED_BILL_ACCESS");
                }
                if (bill.getCurrencyCode() == null || bill.getCurrencyCode().isBlank()) {
                    throw new BusinessException("Bill currency is missing", HttpStatus.BAD_REQUEST, "INVALID_CURRENCY");
                }

                BigDecimal outstanding = bill.getOutstandingBalance().setScale(2, RoundingMode.HALF_UP);
                BigDecimal payableAmount;
                if (request.amount() != null) {
                    if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
                        throw new BusinessException("Payment amount must be greater than zero", HttpStatus.BAD_REQUEST, "INVALID_AMOUNT");
                    }
                    payableAmount = request.amount().setScale(2, RoundingMode.HALF_UP);
                } else {
                    payableAmount = outstanding;
                }

                PaymentProviderType providerType = request.providerType() != null ? request.providerType() : PaymentProviderType.MOCK;
                if (request.paymentMethod() == PaymentMethod.CASH || request.paymentMethod() == PaymentMethod.PAY_AT_COUNTER) {
                    providerType = PaymentProviderType.CASH_REGISTER;
                }

                String requestHash = computeInitiationRequestHash(
                        tenantId, bill.getId(), request.paymentMethod(), providerType, payableAmount, bill.getCurrencyCode()
                );

                // Idempotency check BEFORE rejecting already-paid bills so safe retries of a completed checkout return cleanly
                if (clientIdempotencyKey != null) {
                    Optional<PaymentAttempt> existingOpt = attemptRepository.findByTenantIdAndIdempotencyKey(tenantId, clientIdempotencyKey);
                    if (existingOpt.isPresent()) {
                        PaymentResponse replay = validateAndBuildIdempotentReplay(existingOpt.get(), requestHash);
                        return new PhaseAResult(replay, null, null, null, null, null, null, null);
                    }
                }

                if (bill.getStatus() == BillStatus.PAID) {
                    throw new BusinessException("Bill is already fully settled", HttpStatus.CONFLICT, "BILL_ALREADY_PAID");
                }
                if (bill.getStatus() == BillStatus.VOIDED) {
                    throw new BusinessException("Cannot accept payment for a voided bill", HttpStatus.BAD_REQUEST, "BILL_VOIDED");
                }
                if (payableAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException("Outstanding balance is zero", HttpStatus.CONFLICT, "BILL_ALREADY_PAID");
                }
                if (payableAmount.compareTo(outstanding) > 0) {
                    throw new BusinessException("Payment amount cannot exceed outstanding bill balance", HttpStatus.BAD_REQUEST, "AMOUNT_EXCEEDS_BALANCE");
                }
                if (request.paymentMethod() == PaymentMethod.ONLINE_PAYMENT && !providers.containsKey(providerType)) {
                    throw new BusinessException("Payment gateway provider is not available: " + providerType,
                            HttpStatus.BAD_REQUEST, "GATEWAY_NOT_CONFIGURED");
                }

                String paymentRef = referenceGenerator.generateUniquePaymentReference(tenantId);
                RestaurantPayment payment = new RestaurantPayment(
                        tenantId,
                        bill.getBranchId(),
                        bill,
                        paymentRef,
                        request.paymentMethod(),
                        payableAmount,
                        bill.getCurrencyCode(),
                        providerType
                );
                payment.setNotes(request.notes());
                payment.setPaymentStatus(PaymentStatus.INITIATED);

                String idempotencyKey = clientIdempotencyKey != null ? clientIdempotencyKey : (paymentRef + "-ATTEMPT-1");
                PaymentAttempt attempt = new PaymentAttempt(
                        payment,
                        tenantId,
                        1,
                        idempotencyKey,
                        providerType.name(),
                        payableAmount,
                        bill.getCurrencyCode()
                );
                attempt.setRequestHash(requestHash);
                payment.addAttempt(attempt);

                PaymentAuditRecord audit = new PaymentAuditRecord(
                        payment,
                        tenantId,
                        null,
                        PaymentStatus.INITIATED.name(),
                        "PAYMENT_INITIATED",
                        principal.getUserId(),
                        principal.getType() != null ? principal.getType() : "STAFF",
                        payableAmount,
                        "Payment intent persisted for bill " + bill.getBillNumber()
                );
                payment.addAuditRecord(audit);

                RestaurantPayment saved = paymentRepository.saveAndFlush(payment);
                PaymentAttempt savedAttempt = saved.getAttempts().get(saved.getAttempts().size() - 1);
                return new PhaseAResult(null, saved.getId(), savedAttempt.getId(), saved.getPaymentReference(), bill, saved, savedAttempt, providerType);
            });
        } catch (DataIntegrityViolationException dive) {
            // Concurrent duplicate request with the same client idempotency key raced into DB unique constraint
            if (clientIdempotencyKey != null) {
                return resolveConcurrentIdempotencyReplay(tenantId, clientIdempotencyKey, request);
            }
            throw dive;
        }

        if (phaseA.replayResponse() != null) {
            return phaseA.replayResponse();
        }

        // Phase B: External Gateway HTTP I/O strictly OUTSIDE any database transaction
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            log.warn("Unexpected active DB transaction detected before Phase B external gateway call for payment {}", phaseA.paymentReference());
        }

        PaymentGatewayProvider.GatewayOrderResult gatewayOrderResult = null;
        if (request.paymentMethod() == PaymentMethod.ONLINE_PAYMENT) {
            PaymentGatewayProvider gateway = providers.get(phaseA.providerType());
            try {
                gatewayOrderResult = gateway.createPaymentOrder(phaseA.bill(), phaseA.payment(), phaseA.attempt(), request.returnUrl());
            } catch (Exception unexpected) {
                log.error("Unexpected exception during external gateway call for payment {} — marking ambiguous for reconciliation: {}",
                        phaseA.paymentReference(), unexpected.getMessage());
                gatewayOrderResult = new PaymentGatewayProvider.GatewayOrderResult(
                        false, null, null, null,
                        "GATEWAY_UNEXPECTED_ERROR",
                        "Unexpected gateway error; outcome ambiguous: " + unexpected.getMessage(),
                        null,
                        true
                );
            }
        }

        // Phase C: New short DB Transaction — persist provider result with state-machine protection
        final PaymentGatewayProvider.GatewayOrderResult finalResult = gatewayOrderResult;
        try {
            return transactionTemplate.execute(status -> {
                RestaurantPayment persisted = paymentRepository.findByIdAndTenantIdForUpdate(phaseA.paymentId(), tenantId)
                        .orElseThrow(() -> new BusinessException("Payment record not found", HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND"));
                PaymentAttempt persistedAttempt = persisted.getAttempts().stream()
                        .filter(a -> a.getId().equals(phaseA.attemptId()))
                        .findFirst()
                        .orElse(persisted.getAttempts().get(persisted.getAttempts().size() - 1));

                String redirectUrl = persistedAttempt.getClientRedirectUrl();
                String providerOrderId = persistedAttempt.getProviderOrderId();
                String clientSecretOrKey = null;

                if (finalResult != null) {
                    // Protect against concurrent webhook having already confirmed/refunded the payment
                    if (PaymentStateMachine.isConfirmedOrRefunded(persisted.getPaymentStatus())) {
                        if (finalResult.isSuccess() && persistedAttempt.getProviderOrderId() == null) {
                            persistedAttempt.setProviderOrderId(finalResult.providerOrderId());
                            persistedAttempt.setRawProviderResponse(finalResult.rawResponse());
                            providerOrderId = finalResult.providerOrderId();
                        }
                        persisted.addAuditRecord(new PaymentAuditRecord(
                                persisted,
                                tenantId,
                                persisted.getPaymentStatus().name(),
                                persisted.getPaymentStatus().name(),
                                "PROVIDER_RESPONSE_AFTER_CONFIRMATION",
                                principal.getUserId(),
                                "SYSTEM",
                                persisted.getPayableAmount(),
                                "Preserved confirmed state " + persisted.getPaymentStatus() + " after Phase B returned"
                        ));
                        persisted = paymentRepository.save(persisted);
                        return toPaymentResponse(persisted, providerOrderId, finalResult.clientSecret(), redirectUrl);
                    }

                    if (finalResult.isSuccess()) {
                        persistedAttempt.setProviderOrderId(finalResult.providerOrderId());
                        persistedAttempt.setClientRedirectUrl(finalResult.redirectUrl());
                        persistedAttempt.setRawProviderResponse(finalResult.rawResponse());
                        redirectUrl = finalResult.redirectUrl();
                        providerOrderId = finalResult.providerOrderId();
                        clientSecretOrKey = finalResult.clientSecret();
                        persisted.addAuditRecord(new PaymentAuditRecord(
                                persisted,
                                tenantId,
                                persisted.getPaymentStatus().name(),
                                persisted.getPaymentStatus().name(),
                                "PROVIDER_ORDER_CREATED",
                                principal.getUserId(),
                                "SYSTEM",
                                persisted.getPayableAmount(),
                                "Provider order created: " + providerOrderId
                        ));
                    } else if (finalResult.ambiguous()) {
                        // NEVER mark ambiguous timeout/5xx/malformed response as confirmed FAILED
                        String prevStatus = persisted.getPaymentStatus().name();
                        PaymentStateMachine.assertValidTransition(persisted.getPaymentStatus(), PaymentStatus.PENDING_CONFIRMATION);
                        persisted.setPaymentStatus(PaymentStatus.PENDING_CONFIRMATION);
                        persistedAttempt.setStatus("PENDING_CONFIRMATION");
                        persistedAttempt.setAmbiguousOutcome(true);
                        persistedAttempt.setFailureCode(finalResult.errorCode());
                        persistedAttempt.setFailureMessage(finalResult.errorMessage());
                        persistedAttempt.setRawProviderResponse(finalResult.rawResponse());
                        persisted.addAuditRecord(new PaymentAuditRecord(
                                persisted,
                                tenantId,
                                prevStatus,
                                PaymentStatus.PENDING_CONFIRMATION.name(),
                                "PROVIDER_OUTCOME_AMBIGUOUS",
                                principal.getUserId(),
                                "SYSTEM",
                                persisted.getPayableAmount(),
                                "Ambiguous provider outcome (" + finalResult.errorCode() + "); queued for reconciliation"
                        ));
                    } else {
                        String prevStatus = persisted.getPaymentStatus().name();
                        PaymentStateMachine.assertValidTransition(persisted.getPaymentStatus(), PaymentStatus.FAILED);
                        persistedAttempt.setStatus("FAILED");
                        persistedAttempt.setFailureCode(finalResult.errorCode());
                        persistedAttempt.setFailureMessage(finalResult.errorMessage());
                        persistedAttempt.setRawProviderResponse(finalResult.rawResponse());
                        persisted.setPaymentStatus(PaymentStatus.FAILED);
                        persisted.addAuditRecord(new PaymentAuditRecord(
                                persisted,
                                tenantId,
                                prevStatus,
                                PaymentStatus.FAILED.name(),
                                "PROVIDER_ORDER_REJECTED",
                                principal.getUserId(),
                                "SYSTEM",
                                persisted.getPayableAmount(),
                                "Provider rejected order creation: " + finalResult.errorCode()
                        ));
                    }
                    persisted = paymentRepository.save(persisted);
                }

                log.info("Payment record initiated: Ref: {}, Status: {}, Amount: {} {}, Provider: {}",
                        persisted.getPaymentReference(), persisted.getPaymentStatus(), persisted.getPayableAmount(),
                        persisted.getCurrencyCode(), phaseA.providerType());

                return toPaymentResponse(persisted, providerOrderId, clientSecretOrKey, redirectUrl);
            });
        } catch (RuntimeException dbEx) {
            if (finalResult != null && finalResult.isSuccess()) {
                log.error("CRITICAL_RECONCILIATION_ALERT: Phase C DB persistence failed after provider order creation. " +
                                "paymentId={}, paymentRef={}, providerOrderId={}, error={}",
                        phaseA.paymentId(), phaseA.paymentReference(), finalResult.providerOrderId(), dbEx.getMessage());
            }
            throw dbEx;
        }
    }

    private String resolveIdempotencyKey(String headerKey, String bodyKey) {
        if (headerKey != null && !headerKey.isBlank()) {
            return headerKey.trim();
        }
        if (bodyKey != null && !bodyKey.isBlank()) {
            return bodyKey.trim();
        }
        return null;
    }

    private String computeInitiationRequestHash(UUID tenantId, UUID billId, PaymentMethod method,
                                                PaymentProviderType providerType, BigDecimal amount, String currency) {
        String canonical = tenantId + "|" + billId + "|" + method.name() + "|" + providerType.name()
                + "|" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString() + "|" + currency.toUpperCase();
        return hashSha256(canonical);
    }

    private PaymentResponse validateAndBuildIdempotentReplay(PaymentAttempt existing, String incomingRequestHash) {
        if (existing.getRequestHash() != null && !existing.getRequestHash().equals(incomingRequestHash)) {
            throw new BusinessException(
                    "Idempotency key was already used with a different payment request payload",
                    HttpStatus.CONFLICT,
                    "IDEMPOTENCY_KEY_PAYLOAD_MISMATCH"
            );
        }
        RestaurantPayment payment = existing.getPayment();
        return toPaymentResponse(payment, existing.getProviderOrderId(), null, existing.getClientRedirectUrl());
    }

    private PaymentResponse resolveConcurrentIdempotencyReplay(UUID tenantId, String clientIdempotencyKey, InitiatePaymentRequest request) {
        return transactionTemplate.execute(status -> {
            PaymentAttempt existing = attemptRepository.findByTenantIdAndIdempotencyKey(tenantId, clientIdempotencyKey)
                    .orElseThrow(() -> new BusinessException("Concurrent idempotency conflict", HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT"));
            RestaurantBill bill = existing.getPayment().getBill();
            BigDecimal payable = request.amount() != null
                    ? request.amount().setScale(2, RoundingMode.HALF_UP)
                    : existing.getAmount().setScale(2, RoundingMode.HALF_UP);
            PaymentProviderType providerType = request.providerType() != null ? request.providerType() : existing.getPayment().getPaymentProvider();
            if (request.paymentMethod() == PaymentMethod.CASH || request.paymentMethod() == PaymentMethod.PAY_AT_COUNTER) {
                providerType = PaymentProviderType.CASH_REGISTER;
            }
            String requestHash = computeInitiationRequestHash(
                    tenantId, request.billId(), request.paymentMethod(), providerType, payable, bill.getCurrencyCode()
            );
            return validateAndBuildIdempotentReplay(existing, requestHash);
        });
    }

    /**
     * Settle payment via manual verification or client verification payload.
     * Uses TransactionTemplate so verification failures commit FAILED audit state cleanly before throwing.
     */
    public PaymentResponse settlePayment(DineFlowPrincipal principal, UUID paymentId, VerifyPaymentRequest request) {
        UUID tenantId = principal.getTenantId();
        record SettleOutcome(PaymentResponse response, String verificationFailureMessage) {}

        SettleOutcome outcome = transactionTemplate.execute(status -> {
            RestaurantPayment payment = paymentRepository.findByIdAndTenantIdForUpdate(paymentId, tenantId)
                    .orElseThrow(() -> new BusinessException("Payment record not found", HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND"));

            if (PaymentStateMachine.isConfirmedOrRefunded(payment.getPaymentStatus())) {
                return new SettleOutcome(toPaymentResponse(payment, null, null, null), null);
            }

            if (payment.getPaymentStatus() == PaymentStatus.CANCELLED || payment.getPaymentStatus() == PaymentStatus.REFUNDED) {
                throw new BusinessException("Cannot settle payment in " + payment.getPaymentStatus() + " state", HttpStatus.CONFLICT, "INVALID_PAYMENT_STATE");
            }

            RestaurantBill bill = billRepository.findByIdAndTenantIdForUpdate(payment.getBill().getId(), tenantId)
                    .orElseThrow(() -> new BusinessException("Bill not found", HttpStatus.NOT_FOUND, "BILL_NOT_FOUND"));
            if (principal.getBranchId() != null && payment.getBranchId() != null && !payment.getBranchId().equals(principal.getBranchId())) {
                throw new BusinessException("Unauthorized cross-branch payment settlement", HttpStatus.FORBIDDEN, "CROSS_BRANCH_ACCESS");
            }
            if ("GUEST".equalsIgnoreCase(principal.getType())) {
                if (payment.getPaymentMethod() != PaymentMethod.ONLINE_PAYMENT) {
                    throw new BusinessException("Only authorized restaurant staff can confirm cash or counter payments",
                            HttpStatus.FORBIDDEN, "STAFF_AUTH_REQUIRED");
                }
                if (bill.getCustomerSessionId() == null || !bill.getCustomerSessionId().equals(principal.getUserId())) {
                    throw new BusinessException("Unauthorized cross-guest payment settlement", HttpStatus.FORBIDDEN, "UNAUTHORIZED_PAYMENT_ACCESS");
                }
            }
            if (bill.getStatus() == BillStatus.VOIDED) {
                throw new BusinessException("Cannot settle payment for a voided bill", HttpStatus.CONFLICT, "BILL_VOIDED");
            }
            if (payment.getPayableAmount().compareTo(bill.getOutstandingBalance()) > 0) {
                throw new BusinessException(
                        String.format("Payment amount (%s) exceeds current outstanding bill balance (%s)",
                                payment.getPayableAmount(), bill.getOutstandingBalance()),
                        HttpStatus.CONFLICT,
                        "OVERPAYMENT_PREVENTED"
                );
            }

            PaymentAttempt activeAttempt = payment.getAttempts().isEmpty() ? null : payment.getAttempts().get(payment.getAttempts().size() - 1);

            if (payment.getPaymentMethod() == PaymentMethod.ONLINE_PAYMENT && payment.getPaymentProvider() != PaymentProviderType.INTERNAL) {
                PaymentGatewayProvider gateway = providers.get(payment.getPaymentProvider());
                if (gateway == null) {
                    throw new BusinessException("Payment gateway provider is not configured: " + payment.getPaymentProvider(),
                            HttpStatus.BAD_REQUEST, "GATEWAY_NOT_CONFIGURED");
                }
                // Authoritative order ID must come from persisted attempt (never trust client override)
                String provOrderId = activeAttempt != null ? activeAttempt.getProviderOrderId() : null;

                PaymentGatewayProvider.GatewayVerificationResult verResult = gateway.verifyPayment(
                        provOrderId, request.providerTransactionId(), request.signature()
                );

                if (!verResult.isVerified()) {
                    String prev = payment.getPaymentStatus().name();
                    payment.setPaymentStatus(PaymentStatus.FAILED);
                    if (activeAttempt != null) {
                        activeAttempt.setStatus("FAILED");
                        activeAttempt.setFailureCode(verResult.errorCode());
                        activeAttempt.setFailureMessage(verResult.errorMessage());
                    }
                    payment.addAuditRecord(new PaymentAuditRecord(
                            payment,
                            tenantId,
                            prev,
                            PaymentStatus.FAILED.name(),
                            "VERIFICATION_FAILED",
                            principal.getUserId(),
                            principal.getType() != null ? principal.getType() : "STAFF",
                            payment.getPayableAmount(),
                            "Provider signature verification failed: " + verResult.errorMessage()
                    ));
                    paymentRepository.save(payment);
                    return new SettleOutcome(null, verResult.errorMessage());
                }
            }

            String prevStatus = payment.getPaymentStatus().name();
            payment.setPaymentStatus(PaymentStatus.SUCCEEDED);
            payment.setPaidAmount(payment.getPayableAmount());
            payment.setProviderPaymentId(request.providerTransactionId());
            payment.setCollectedByUserId(principal.getUserId());
            payment.setSettledAt(Instant.now());

            if (activeAttempt != null) {
                activeAttempt.setStatus("SUCCEEDED");
                activeAttempt.setProviderTransactionId(request.providerTransactionId());
            }

            applyPaymentSettlementToBill(bill, payment.getPayableAmount());

            PaymentAuditRecord audit = new PaymentAuditRecord(
                    payment,
                    tenantId,
                    prevStatus,
                    PaymentStatus.SUCCEEDED.name(),
                    "SETTLED_CONFIRMED",
                    principal.getUserId(),
                    principal.getType() != null ? principal.getType() : "STAFF",
                    payment.getPayableAmount(),
                    "Payment successfully settled with provider txn " + request.providerTransactionId()
            );
            payment.addAuditRecord(audit);

            RestaurantPayment saved = paymentRepository.save(payment);
            return new SettleOutcome(toPaymentResponse(saved, null, null, null), null);
        });

        if (outcome.verificationFailureMessage() != null) {
            throw new BusinessException("Payment verification failed with provider: " + outcome.verificationFailureMessage(),
                    HttpStatus.BAD_REQUEST, "PAYMENT_VERIFICATION_FAILED");
        }
        return outcome.response();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void processProviderWebhook(String providerName, String rawPayload, Map<String, String> headers) {
        PaymentProviderType providerType;
        try {
            providerType = PaymentProviderType.valueOf(providerName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Unsupported webhook provider: " + providerName, HttpStatus.BAD_REQUEST, "UNSUPPORTED_PROVIDER");
        }

        PaymentGatewayProvider gateway = providers.get(providerType);
        if (gateway == null) {
            throw new BusinessException("Provider gateway not configured: " + providerName, HttpStatus.BAD_REQUEST, "GATEWAY_NOT_CONFIGURED");
        }

        PaymentGatewayProvider.GatewayWebhookResult whResult = gateway.verifyWebhookEvent(rawPayload, headers);
        if (!whResult.isVerified()) {
            throw new BusinessException("Webhook signature verification failed: " + whResult.errorMessage(),
                    HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK_SIGNATURE");
        }

        String eventId = headers.entrySet().stream()
                .filter(e -> "x-razorpay-event-id".equalsIgnoreCase(e.getKey()))
                .map(Map.Entry::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst()
                .orElse("evt_" + whResult.providerTransactionId());
        String payloadHash = hashSha256(rawPayload);

        if (webhookEventRepository.existsByProviderNameAndProviderEventId(providerType.name(), eventId)) {
            log.info("Duplicate webhook event ignored: {}/{}", providerType.name(), eventId);
            return;
        }

        String eventType = whResult.eventType() != null && !whResult.eventType().isBlank()
                ? whResult.eventType()
                : "payment.captured";

        PaymentWebhookEvent webhookEvent = new PaymentWebhookEvent(
                providerType.name(),
                eventId,
                eventType,
                payloadHash,
                rawPayload
        );
        webhookEvent.setProviderPaymentId(whResult.providerTransactionId());

        try {
            // Lookup order: providerOrderId -> providerTransactionId -> paymentReference (handles webhook arriving before Phase C!)
            Optional<PaymentAttempt> attemptOpt = Optional.empty();
            if (whResult.providerOrderId() != null && !whResult.providerOrderId().isBlank()) {
                attemptOpt = attemptRepository.findFirstByProviderOrderId(whResult.providerOrderId());
            }
            if (attemptOpt.isEmpty() && whResult.providerTransactionId() != null && !whResult.providerTransactionId().isBlank()) {
                attemptOpt = attemptRepository.findFirstByProviderTransactionId(whResult.providerTransactionId());
            }
            if (attemptOpt.isEmpty() && whResult.paymentReference() != null && !whResult.paymentReference().isBlank()) {
                Optional<RestaurantPayment> refPaymentOpt = paymentRepository.findByPaymentReferenceForUpdate(whResult.paymentReference());
                if (refPaymentOpt.isPresent() && !refPaymentOpt.get().getAttempts().isEmpty()) {
                    List<PaymentAttempt> attempts = refPaymentOpt.get().getAttempts();
                    attemptOpt = Optional.of(attempts.get(attempts.size() - 1));
                }
            }

            if (attemptOpt.isPresent()) {
                PaymentAttempt rawAttempt = attemptOpt.get();
                // Lock payment and bill rows to serialize concurrent webhook + settle + Phase C writes
                RestaurantPayment payment = paymentRepository.findByIdAndTenantIdForUpdate(
                                rawAttempt.getPayment().getId(), rawAttempt.getTenantId())
                        .orElse(rawAttempt.getPayment());
                PaymentAttempt attempt = payment.getAttempts().stream()
                        .filter(a -> a.getId().equals(rawAttempt.getId()))
                        .findFirst()
                        .orElse(rawAttempt);

                if (attempt.getProviderOrderId() == null && whResult.providerOrderId() != null && !whResult.providerOrderId().isBlank()) {
                    attempt.setProviderOrderId(whResult.providerOrderId());
                }
                webhookEvent.setPaymentReference(payment.getPaymentReference());

                // Out-of-order protection: NEVER downgrade an already SUCCEEDED / PARTIALLY_REFUNDED / REFUNDED payment
                if (PaymentStateMachine.isConfirmedOrRefunded(payment.getPaymentStatus())) {
                    webhookEvent.setStatus("IGNORED");
                    webhookEvent.setProcessedAt(Instant.now());
                    webhookEventRepository.save(webhookEvent);
                    return;
                }

                // Explicit event type dispatch
                if ("payment.failed".equalsIgnoreCase(eventType)) {
                    String prevStatus = payment.getPaymentStatus().name();
                    payment.setPaymentStatus(PaymentStatus.FAILED);
                    attempt.setStatus("FAILED");
                    attempt.setFailureCode("WEBHOOK_PAYMENT_FAILED");
                    payment.addAuditRecord(new PaymentAuditRecord(
                            payment,
                            payment.getTenantId(),
                            prevStatus,
                            PaymentStatus.FAILED.name(),
                            "WEBHOOK_PAYMENT_FAILED",
                            null,
                            "WEBHOOK",
                            payment.getPayableAmount(),
                            "Provider reported payment failure via webhook event " + eventId
                    ));
                    paymentRepository.save(payment);
                    webhookEvent.setStatus("PROCESSED");
                    webhookEvent.setProcessedAt(Instant.now());
                    webhookEventRepository.save(webhookEvent);
                    return;
                }

                if (!"payment.captured".equalsIgnoreCase(eventType) && !"order.paid".equalsIgnoreCase(eventType)) {
                    webhookEvent.setStatus("IGNORED");
                    webhookEvent.setProcessedAt(Instant.now());
                    webhookEventRepository.save(webhookEvent);
                    return;
                }

                // Strict amount & currency verification
                if (whResult.verifiedAmount() != null && whResult.verifiedAmount().compareTo(payment.getPayableAmount()) != 0) {
                    webhookEvent.setStatus("FAILED");
                    webhookEvent.setErrorMessage("Amount mismatch: expected " + payment.getPayableAmount() + ", got " + whResult.verifiedAmount());
                    webhookEventRepository.save(webhookEvent);
                    return;
                }
                if (whResult.currencyCode() != null && !whResult.currencyCode().equalsIgnoreCase(payment.getCurrencyCode())) {
                    webhookEvent.setStatus("FAILED");
                    webhookEvent.setErrorMessage("Currency mismatch: expected " + payment.getCurrencyCode() + ", got " + whResult.currencyCode());
                    webhookEventRepository.save(webhookEvent);
                    return;
                }

                RestaurantBill bill = billRepository.findByIdAndTenantIdForUpdate(payment.getBill().getId(), payment.getTenantId())
                        .orElse(payment.getBill());
                if (payment.getPayableAmount().compareTo(bill.getOutstandingBalance()) > 0) {
                    attempt.setManualReviewRequired(true);
                    attempt.setFailureCode("OVERPAYMENT_DETECTED");
                    attempt.setFailureMessage("Captured webhook amount exceeds remaining bill balance; manual refund required");
                    payment.addAuditRecord(new PaymentAuditRecord(
                            payment,
                            payment.getTenantId(),
                            payment.getPaymentStatus().name(),
                            payment.getPaymentStatus().name(),
                            "OVERPAYMENT_MANUAL_REVIEW_REQUIRED",
                            null,
                            "WEBHOOK",
                            payment.getPayableAmount(),
                            "Overpayment detected on webhook " + eventId + "; flagged for manual ops refund"
                    ));
                    paymentRepository.save(payment);
                    webhookEvent.setStatus("FAILED");
                    webhookEvent.setErrorMessage("Overpayment detected: payment " + payment.getPayableAmount()
                            + " exceeds outstanding bill balance " + bill.getOutstandingBalance());
                    webhookEventRepository.save(webhookEvent);
                    return;
                }

                String prevStatus = payment.getPaymentStatus().name();
                payment.setPaymentStatus(PaymentStatus.SUCCEEDED);
                payment.setPaidAmount(payment.getPayableAmount());
                payment.setProviderPaymentId(whResult.providerTransactionId());
                payment.setSettledAt(Instant.now());
                attempt.setStatus("SUCCEEDED");
                attempt.setAmbiguousOutcome(false);
                attempt.setProviderTransactionId(whResult.providerTransactionId());

                applyPaymentSettlementToBill(bill, payment.getPayableAmount());

                PaymentAuditRecord audit = new PaymentAuditRecord(
                        payment,
                        payment.getTenantId(),
                        prevStatus,
                        PaymentStatus.SUCCEEDED.name(),
                        "WEBHOOK_VERIFIED",
                        null,
                        "WEBHOOK",
                        payment.getPayableAmount(),
                        "Payment settled via verified webhook event " + eventId
                );
                payment.addAuditRecord(audit);
                paymentRepository.save(payment);
            }

            webhookEvent.setStatus("PROCESSED");
            webhookEvent.setProcessedAt(Instant.now());
        } catch (Exception e) {
            log.error("Failed to apply webhook event {}", eventId, e);
            webhookEvent.setStatus("FAILED");
            webhookEvent.setErrorMessage(e.getMessage());
            webhookEvent.incrementRetryCount();
        } finally {
            webhookEventRepository.save(webhookEvent);
        }
    }

    public ReconciliationReport reconcilePendingPayments(UUID tenantId) {
        return reconcilePendingPayments(tenantId, "SCHEDULER");
    }

    /**
     * Reconciles bounded batches of stale and ambiguous (PENDING_CONFIRMATION) payments using
     * non-transactional provider polling with short per-payment locked transactions, exponential
     * backoff with jitter, and manual-review dead-lettering upon retry exhaustion.
     */
    public ReconciliationReport reconcilePendingPayments(UUID tenantId, String triggeredBy) {
        record CandidateSnapshot(UUID paymentId, UUID paymentTenantId, String paymentReference,
                                 PaymentProviderType providerType, String providerOrderId,
                                 PaymentStatus status, Instant createdAt,
                                 boolean manualReviewRequired, Instant nextReconciliationAt) {}

        long startMs = System.currentTimeMillis();
        Instant now = Instant.now();
        EnumSet<PaymentStatus> recoverableStatuses = EnumSet.of(
                PaymentStatus.INITIATED, PaymentStatus.PENDING, PaymentStatus.PROCESSING, PaymentStatus.PENDING_CONFIRMATION
        );

        // Short TX 1: Start reconciliation log & fetch bounded batch (max 50 per cycle — never unbounded findAll)
        UUID reconLogId = transactionTemplate.execute(status -> {
            PaymentReconciliationLog reconLog = new PaymentReconciliationLog(tenantId, "STALE_AND_AMBIGUOUS_SCAN");
            reconLog.setTriggeredBy(triggeredBy != null ? triggeredBy : "SCHEDULER");
            return reconciliationLogRepository.save(reconLog).getId();
        });

        try {
            List<CandidateSnapshot> candidates = transactionTemplate.execute(status -> {
                List<RestaurantPayment> payments = paymentRepository
                        .findReconciliationBatch(tenantId, recoverableStatuses, PageRequest.of(0, 50))
                        .getContent();
                return payments.stream().map(p -> {
                    PaymentAttempt attempt = p.getAttempts().isEmpty() ? null : p.getAttempts().get(p.getAttempts().size() - 1);
                    String orderId = attempt != null ? attempt.getProviderOrderId() : null;
                    boolean manualReview = attempt != null && attempt.isManualReviewRequired();
                    Instant nextAt = attempt != null ? attempt.getNextReconciliationAt() : null;
                    return new CandidateSnapshot(p.getId(), p.getTenantId(), p.getPaymentReference(),
                            p.getPaymentProvider(), orderId, p.getPaymentStatus(), p.getCreatedAt(), manualReview, nextAt);
                }).toList();
            });

            Instant fifteenMinutesAgo = now.minus(Duration.ofMinutes(15));
            int scanned = 0;
            int resolved = 0;
            int discrepancies = 0;
            int timedOut = 0;
            int retriesScheduled = 0;
            StringBuilder reportBuilder = new StringBuilder();

            for (CandidateSnapshot candidate : candidates) {
                if (candidate.manualReviewRequired()) {
                    continue; // Dead-lettered for manual operator review
                }
                if (candidate.nextReconciliationAt() != null && candidate.nextReconciliationAt().isAfter(now)) {
                    continue; // Still inside exponential backoff cooldown window
                }

                scanned++;
                boolean isStale = candidate.createdAt() != null && candidate.createdAt().isBefore(fifteenMinutesAgo);
                boolean isAmbiguous = candidate.status() == PaymentStatus.PENDING_CONFIRMATION;

                if (!isStale && !isAmbiguous) {
                    continue;
                }

                if (candidate.providerOrderId() != null && candidate.providerType() != PaymentProviderType.CASH_REGISTER) {
                    PaymentGatewayProvider gateway = providers.get(candidate.providerType());
                    if (gateway == null) {
                        continue;
                    }

                    // External provider status poll runs STRICTLY OUTSIDE any database transaction
                    PaymentGatewayProvider.GatewayVerificationResult pollResult;
                    try {
                        pollResult = gateway.fetchOrderStatus(candidate.providerOrderId());
                    } catch (Exception ex) {
                        pollResult = new PaymentGatewayProvider.GatewayVerificationResult(
                                false, null, null, null, "POLL_EXCEPTION", ex.getMessage(), true
                        );
                    }

                    final PaymentGatewayProvider.GatewayVerificationResult finalPoll = pollResult;
                    String outcome = transactionTemplate.execute(status -> {
                        RestaurantPayment payment = paymentRepository.findByIdAndTenantIdForUpdate(
                                candidate.paymentId(), candidate.paymentTenantId()).orElse(null);
                        if (payment == null || PaymentStateMachine.isConfirmedOrRefunded(payment.getPaymentStatus())) {
                            return "SKIPPED";
                        }
                        PaymentAttempt attempt = payment.getAttempts().isEmpty() ? null : payment.getAttempts().get(payment.getAttempts().size() - 1);

                        if (finalPoll.isVerified()) {
                            RestaurantBill bill = billRepository.findByIdAndTenantIdForUpdate(payment.getBill().getId(), payment.getTenantId())
                                    .orElse(payment.getBill());
                            if (payment.getPayableAmount().compareTo(bill.getOutstandingBalance()) > 0) {
                                if (attempt != null) {
                                    attempt.setManualReviewRequired(true);
                                }
                                payment.addAuditRecord(new PaymentAuditRecord(
                                        payment, payment.getTenantId(), payment.getPaymentStatus().name(),
                                        payment.getPaymentStatus().name(), "RECONCILIATION_OVERPAYMENT_GUARD",
                                        null, "RECONCILER", payment.getPayableAmount(),
                                        "Provider captured payment exceeds remaining bill balance; manual review required"
                                ));
                                paymentRepository.save(payment);
                                return "DISCREPANCY";
                            }
                            String prev = payment.getPaymentStatus().name();
                            applyPaymentSettlementToBill(bill, payment.getPayableAmount());
                            payment.setPaymentStatus(PaymentStatus.SUCCEEDED);
                            payment.setPaidAmount(payment.getPayableAmount());
                            payment.setProviderPaymentId(finalPoll.providerTransactionId());
                            payment.setSettledAt(Instant.now());
                            if (attempt != null) {
                                attempt.setStatus("SUCCEEDED");
                                attempt.setAmbiguousOutcome(false);
                                attempt.setManualReviewRequired(false);
                                attempt.setNextReconciliationAt(null);
                                attempt.setProviderTransactionId(finalPoll.providerTransactionId());
                            }
                            payment.addAuditRecord(new PaymentAuditRecord(
                                    payment, payment.getTenantId(), prev, PaymentStatus.SUCCEEDED.name(),
                                    "RECONCILIATION_CONFIRMED", null, "RECONCILER", payment.getPayableAmount(),
                                    "Settled via authoritative provider status poll"
                            ));
                            paymentRepository.save(payment);
                            return "RESOLVED";
                        } else if (finalPoll.transientError()) {
                            String prev = payment.getPaymentStatus().name();
                            payment.setPaymentStatus(PaymentStatus.PENDING_CONFIRMATION);
                            int nextRetry = 1;
                            boolean exhausted = false;
                            if (attempt != null) {
                                attempt.setStatus("PENDING_CONFIRMATION");
                                attempt.setAmbiguousOutcome(true);
                                nextRetry = attempt.getReconciliationRetryCount() + 1;
                                attempt.setReconciliationRetryCount(nextRetry);
                                exhausted = ReconciliationRetryPolicy.isExhausted(nextRetry);
                                if (exhausted) {
                                    attempt.setManualReviewRequired(true);
                                    attempt.setNextReconciliationAt(null);
                                } else {
                                    attempt.setNextReconciliationAt(ReconciliationRetryPolicy.computeNextAttemptAt(Instant.now(), nextRetry));
                                }
                            }
                            payment.addAuditRecord(new PaymentAuditRecord(
                                    payment, payment.getTenantId(), prev, PaymentStatus.PENDING_CONFIRMATION.name(),
                                    exhausted ? "RECONCILIATION_RETRY_EXHAUSTED_MANUAL_REVIEW" : "RECONCILIATION_PROVIDER_UNAVAILABLE",
                                    null, "RECONCILER", payment.getPayableAmount(),
                                    exhausted
                                            ? "Retry limit exhausted (" + nextRetry + "); flagged for manual operator review"
                                            : "Provider unavailable (" + finalPoll.errorCode() + "); backoff retry #" + nextRetry
                            ));
                            paymentRepository.save(payment);
                            return exhausted ? "MANUAL_REVIEW" : "DISCREPANCY";
                        } else if (isStale) {
                            String prev = payment.getPaymentStatus().name();
                            payment.setPaymentStatus(PaymentStatus.CANCELLED);
                            if (attempt != null) {
                                attempt.setStatus("TIMED_OUT");
                                attempt.setAmbiguousOutcome(false);
                                attempt.setNextReconciliationAt(null);
                            }
                            payment.addAuditRecord(new PaymentAuditRecord(
                                    payment, payment.getTenantId(), prev, PaymentStatus.CANCELLED.name(),
                                    "RECONCILIATION_EXPIRED", null, "RECONCILER", payment.getPayableAmount(),
                                    "Provider confirmed order not captured after expiry window"
                            ));
                            paymentRepository.save(payment);
                            return "TIMED_OUT";
                        }
                        return "PENDING";
                    });

                    switch (outcome) {
                        case "RESOLVED" -> {
                            resolved++;
                            reportBuilder.append(String.format("Payment %s resolved to SUCCEEDED. ", candidate.paymentReference()));
                        }
                        case "MANUAL_REVIEW" -> {
                            discrepancies++;
                            retriesScheduled++;
                            reportBuilder.append(String.format("Payment %s exhausted retries -> MANUAL_REVIEW. ", candidate.paymentReference()));
                        }
                        case "DISCREPANCY" -> {
                            discrepancies++;
                            retriesScheduled++;
                            reportBuilder.append(String.format("Transient error reconciling %s (%s); backoff scheduled. ",
                                    candidate.paymentReference(), finalPoll.errorCode()));
                        }
                        case "TIMED_OUT" -> {
                            timedOut++;
                            reportBuilder.append(String.format("Payment %s timed out and CANCELLED. ", candidate.paymentReference()));
                        }
                        default -> { /* still within window or already resolved */ }
                    }
                } else if (isStale) {
                    String outcome = transactionTemplate.execute(status -> {
                        RestaurantPayment payment = paymentRepository.findByIdAndTenantIdForUpdate(
                                candidate.paymentId(), candidate.paymentTenantId()).orElse(null);
                        if (payment == null || PaymentStateMachine.isConfirmedOrRefunded(payment.getPaymentStatus())) {
                            return "SKIPPED";
                        }
                        String prev = payment.getPaymentStatus().name();
                        payment.setPaymentStatus(PaymentStatus.CANCELLED);
                        payment.addAuditRecord(new PaymentAuditRecord(
                                payment, payment.getTenantId(), prev, PaymentStatus.CANCELLED.name(),
                                "RECONCILIATION_EXPIRED_NO_ORDER_ID", null, "RECONCILER", payment.getPayableAmount(),
                                "Expired without provider order confirmation"
                        ));
                        paymentRepository.save(payment);
                        return "TIMED_OUT";
                    });
                    if ("TIMED_OUT".equals(outcome)) {
                        timedOut++;
                        reportBuilder.append(String.format("Payment %s expired without provider order ID and CANCELLED. ", candidate.paymentReference()));
                    }
                } else {
                    discrepancies++;
                    reportBuilder.append(String.format("Payment %s awaiting webhook/manual confirmation (no providerOrderId). ", candidate.paymentReference()));
                }
            }

            // Short TX 2: Finalize reconciliation log with duration & retry metrics
            final int finalScanned = scanned;
            final int finalResolved = resolved;
            final int finalDiscrepancies = discrepancies;
            final int finalRetries = retriesScheduled;
            final String finalDetails = reportBuilder.toString();
            final long durationMs = System.currentTimeMillis() - startMs;
            transactionTemplate.executeWithoutResult(status -> {
                reconciliationLogRepository.findById(reconLogId).ifPresent(reconLog -> {
                    reconLog.setScannedCount(finalScanned);
                    reconLog.setReconciledCount(finalResolved);
                    reconLog.setMismatchCount(finalDiscrepancies);
                    reconLog.setRetryCount(finalRetries);
                    reconLog.setDurationMs(durationMs);
                    reconLog.setDiscrepancyDetails(finalDetails);
                    reconLog.setExecutionStatus("COMPLETED");
                    reconLog.setCompletedAt(Instant.now());
                    reconciliationLogRepository.save(reconLog);
                });
            });

            return new ReconciliationReport(scanned, resolved, discrepancies, timedOut, finalDetails);
        } catch (RuntimeException fatal) {
            long durationMs = System.currentTimeMillis() - startMs;
            transactionTemplate.executeWithoutResult(status -> {
                reconciliationLogRepository.findById(reconLogId).ifPresent(reconLog -> {
                    reconLog.setDurationMs(durationMs);
                    reconLog.setExecutionStatus("FAILED");
                    reconLog.setDiscrepancyDetails("Job crashed: " + fatal.getMessage());
                    reconLog.setCompletedAt(Instant.now());
                    reconciliationLogRepository.save(reconLog);
                });
            });
            throw fatal;
        }
    }

    @Transactional(readOnly = true)
    public WebhookProcessingMetrics getWebhookMetrics() {
        return getWebhookMetrics(null);
    }

    @Transactional(readOnly = true)
    public WebhookProcessingMetrics getWebhookMetrics(UUID tenantId) {
        List<PaymentWebhookEvent> events = webhookEventRepository.findAll();
        long received = events.size();
        long processed = events.stream().filter(e -> "PROCESSED".equals(e.getStatus())).count();
        long ignored = events.stream().filter(e -> "IGNORED".equals(e.getStatus())).count();
        long failed = events.stream().filter(e -> "FAILED".equals(e.getStatus())).count();

        Instant staleCutoff = Instant.now().minus(Duration.ofMinutes(15));
        long pendingStale = tenantId != null
                ? paymentRepository.countByTenantIdAndPaymentStatusAndCreatedAtBefore(tenantId, PaymentStatus.INITIATED, staleCutoff)
                : paymentRepository.findAll().stream()
                .filter(p -> p.getPaymentStatus() == PaymentStatus.INITIATED && p.getCreatedAt().isBefore(staleCutoff))
                .count();

        Map<String, Long> byType = events.stream()
                .collect(Collectors.groupingBy(PaymentWebhookEvent::getEventType, Collectors.counting()));

        return new WebhookProcessingMetrics(received, processed, ignored, failed, pendingStale, byType);
    }

    private void applyPaymentSettlementToBill(RestaurantBill bill, BigDecimal payableAmount) {
        BigDecimal newPaidAmount = bill.getPaidAmount().add(payableAmount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal newOutstanding = bill.getGrandTotal().subtract(newPaidAmount).setScale(2, RoundingMode.HALF_UP);
        if (newOutstanding.compareTo(BigDecimal.ZERO) < 0) {
            newOutstanding = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        bill.setPaidAmount(newPaidAmount);
        bill.setOutstandingBalance(newOutstanding);

        if (newOutstanding.compareTo(BigDecimal.ZERO) == 0) {
            bill.setStatus(BillStatus.PAID);
            bill.setPaidAt(Instant.now());
        } else {
            bill.setStatus(BillStatus.PARTIALLY_PAID);
        }
        billRepository.save(bill);
        Map<String, Object> paymentPayload = Map.of(
                "billId", bill.getId().toString(),
                "billNumber", bill.getBillNumber(),
                "billStatus", bill.getStatus().name(),
                "paidAmount", bill.getPaidAmount(),
                "outstandingBalance", bill.getOutstandingBalance()
        );
        eventPublisher.publishEvent(RealtimeEvent.of(
                "PAYMENT_SETTLED",
                bill.getTenantId(),
                bill.getBranchId(),
                "payments",
                bill.getId(),
                paymentPayload
        ));
        if (bill.getCustomerSessionId() != null) {
            eventPublisher.publishEvent(RealtimeEvent.of(
                    "PAYMENT_SETTLED",
                    bill.getTenantId(),
                    bill.getBranchId(),
                    "session",
                    bill.getCustomerSessionId(),
                    paymentPayload
            ));
        }
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listPayments(DineFlowPrincipal principal, PaymentStatus status, String search, UUID requestedBranchId) {
        UUID tenantId = principal.getTenantId();
        UUID effectiveBranch = principal.getBranchId() != null ? principal.getBranchId() : requestedBranchId;
        if (principal.getBranchId() != null && requestedBranchId != null && !principal.getBranchId().equals(requestedBranchId)) {
            throw new BusinessException("Unauthorized cross-branch payment access", HttpStatus.FORBIDDEN, "CROSS_BRANCH_ACCESS");
        }

        String q = search != null ? search.trim().toLowerCase() : null;
        List<RestaurantPayment> payments = effectiveBranch != null
                ? paymentRepository.findAllByTenantIdAndBranchIdOrderByCreatedAtDesc(tenantId, effectiveBranch)
                : paymentRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId);

        return payments.stream()
                .filter(p -> status == null || p.getPaymentStatus() == status)
                .filter(p -> q == null || q.isEmpty()
                        || p.getPaymentReference().toLowerCase().contains(q)
                        || (p.getProviderPaymentId() != null && p.getProviderPaymentId().toLowerCase().contains(q))
                        || (p.getBill() != null && p.getBill().getBillNumber().toLowerCase().contains(q)))
                .map(p -> toPaymentResponse(p, null, null, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(DineFlowPrincipal principal, UUID paymentId) {
        UUID tenantId = principal.getTenantId();
        RestaurantPayment payment = paymentRepository.findByIdAndTenantId(paymentId, tenantId)
                .orElseThrow(() -> new BusinessException("Payment not found", HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND"));

        if (principal.getBranchId() != null && payment.getBranchId() != null && !payment.getBranchId().equals(principal.getBranchId())) {
            throw new BusinessException("Unauthorized cross-branch payment access", HttpStatus.FORBIDDEN, "CROSS_BRANCH_ACCESS");
        }
        if ("GUEST".equalsIgnoreCase(principal.getType())
                && (payment.getBill().getCustomerSessionId() == null || !payment.getBill().getCustomerSessionId().equals(principal.getUserId()))) {
            throw new BusinessException("Unauthorized cross-guest payment access", HttpStatus.FORBIDDEN, "UNAUTHORIZED_PAYMENT_ACCESS");
        }

        PaymentAttempt activeAttempt = payment.getAttempts().isEmpty() ? null : payment.getAttempts().get(payment.getAttempts().size() - 1);
        String provOrderId = activeAttempt != null ? activeAttempt.getProviderOrderId() : null;
        String redirectUrl = activeAttempt != null ? activeAttempt.getClientRedirectUrl() : null;

        return toPaymentResponse(payment, provOrderId, null, redirectUrl);
    }

    public PaymentResponse toPaymentResponse(RestaurantPayment payment, String providerOrderId, String clientSecretOrKeyId, String redirectUrl) {
        return new PaymentResponse(
                payment.getId(),
                payment.getTenantId(),
                payment.getBranchId(),
                payment.getBill().getId(),
                payment.getPaymentReference(),
                payment.getPaymentMethod().name(),
                payment.getPaymentStatus().name(),
                payment.getCurrencyCode(),
                payment.getPayableAmount(),
                payment.getPaidAmount(),
                payment.getRefundedAmount(),
                payment.getPaymentProvider().name(),
                payment.getProviderPaymentId(),
                providerOrderId,
                clientSecretOrKeyId,
                redirectUrl,
                payment.getNotes(),
                payment.getSettledAt(),
                payment.getCreatedAt()
        );
    }

    private String hashSha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "hash_" + text.hashCode();
        }
    }
}
