# DineFlow Payment Transaction Boundary & Financial Reliability Architecture

This document describes the Sprint 6 Master Prompt 03 hardening of DineFlow's payment initiation, webhook processing, refund execution, and reconciliation architecture.

---

## 1. Before-and-After Sequence Diagrams

### Before (Sprint 5 — Single `@Transactional` Wrapping External HTTP Call)

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant PaymentService as PaymentProcessingService
    participant DB as PostgreSQL (HikariCP)
    participant Razorpay as Razorpay Orders API

    Client->>PaymentService: POST /api/v1/payments/initiate
    PaymentService->>DB: BEGIN TX (acquires DB connection + locks)
    PaymentService->>DB: Validate bill & insert payment/attempt
    PaymentService->>Razorpay: POST /v1/orders (slow/unreliable network I/O)
    Note over DB,Razorpay: Connection pool slot & row locks held during network latency/timeout!
    Razorpay-->>PaymentService: Timeout / 5xx / 200 OK
    alt Timeout or Error
        PaymentService->>DB: Mark FAILED or ROLLBACK (intent lost!)
    else 200 OK
        PaymentService->>DB: Update providerOrderId & COMMIT TX
    end
    PaymentService-->>Client: PaymentResponse
```

### After (Sprint 6 — 3-Phase Transaction Boundaries + Idempotency + Ambiguous Recovery)

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant PaymentService as PaymentProcessingService
    participant DB as PostgreSQL (HikariCP)
    participant Razorpay as Razorpay Orders API
    participant Reconciler as ScheduledReconciliationJob

    Client->>PaymentService: POST /api/v1/payments/initiate (Idempotency-Key)

    rect rgb(235, 248, 255)
        Note over PaymentService,DB: Phase A — Short DB Transaction (Persist Intent)
        PaymentService->>DB: BEGIN TX -> SELECT bill FOR UPDATE
        PaymentService->>DB: Validate ownership, currency, balance, idempotencyKey + requestHash
        PaymentService->>DB: INSERT payment (INITIATED) + attempt + audit
        PaymentService->>DB: COMMIT TX (DB connection released immediately!)
    end

    rect rgb(255, 248, 235)
        Note over PaymentService,Razorpay: Phase B — Strictly Outside DB Transaction
        PaymentService->>Razorpay: POST /v1/orders (X-Idempotency-Key, connect/read timeouts)
        Razorpay-->>PaymentService: 200 OK / 4xx Rejected / Timeout / 5xx / Malformed JSON
    end

    rect rgb(235, 255, 240)
        Note over PaymentService,DB: Phase C — New Short DB Transaction (Persist Result)
        PaymentService->>DB: BEGIN TX -> SELECT payment FOR UPDATE
        alt Already SUCCEEDED via early webhook
            PaymentService->>DB: Preserve SUCCEEDED, backfill providerOrderId if null, audit
        else 200 OK
            PaymentService->>DB: Persist providerOrderId + metadata, audit
        else Timeout / 5xx / Malformed (Ambiguous)
            PaymentService->>DB: Transition to PENDING_CONFIRMATION (ambiguous_outcome=true), audit
        else Definite 4xx Rejection
            PaymentService->>DB: Transition to FAILED, audit
        end
        PaymentService->>DB: COMMIT TX
    end

    PaymentService-->>Client: PaymentResponse

    opt Ambiguous or Stale Payment Recovery
        Reconciler->>Razorpay: GET /v1/orders/{id}/payments (OUTSIDE DB TX)
        Reconciler->>DB: Short TX per payment -> SUCCEEDED / retain PENDING_CONFIRMATION / CANCELLED
    end
```

---

## 2. Updated Payment State Machine (`PaymentStateMachine`)

```mermaid
stateDiagram-v2
    [*] --> CREATED
    CREATED --> INITIATED: Phase A Intent Persisted
    INITIATED --> PENDING_CONFIRMATION: Phase B Timeout / 5xx / Malformed JSON
    INITIATED --> FAILED: Definite 4xx Rejection / Webhook payment.failed
    INITIATED --> SUCCEEDED: Verified Settle / Webhook / Reconciliation
    INITIATED --> CANCELLED: Reconciliation Confirmed Not Captured (Expired)

    PENDING_CONFIRMATION --> SUCCEEDED: Webhook or Reconciliation Poll Confirmed Capture
    PENDING_CONFIRMATION --> PENDING_CONFIRMATION: Provider Status Temporarily Unavailable (5xx/Timeout)
    PENDING_CONFIRMATION --> FAILED: Webhook payment.failed
    PENDING_CONFIRMATION --> CANCELLED: Expired & Confirmed Not Captured

    FAILED --> SUCCEEDED: Late Authoritative Capture Webhook (Recovery Only)
    CANCELLED --> SUCCEEDED: Late Authoritative Capture Webhook (Recovery Only)

    SUCCEEDED --> PARTIALLY_REFUNDED: Partial Refund Executed
    SUCCEEDED --> REFUNDED: Full Refund Executed / Staff Reversal
    PARTIALLY_REFUNDED --> PARTIALLY_REFUNDED: Additional Partial Refund
    PARTIALLY_REFUNDED --> REFUNDED: Remaining Balance Refunded

    note right of SUCCEEDED
        Monotonic Protection:
        SUCCEEDED, PARTIALLY_REFUNDED, and REFUNDED
        can NEVER be downgraded by delayed failures,
        timeouts, or out-of-order Phase C writes.
    end note
```

---

## 3. Transaction Boundary & Concurrency Guarantees

1. **Zero Network I/O Inside DB Transactions**:
   - `PaymentProcessingService.initiatePayment()` uses three `TransactionTemplate` boundaries (Phase A short TX -> Phase B non-TX HTTP -> Phase C short TX).
   - `PaymentProcessingService.reconcilePendingPayments()` removes outer `@Transactional` and polls `gateway.fetchOrderStatus()` outside any DB transaction, committing each resolved payment in its own short locked transaction.
   - `RefundManagementService.executeRefund()` uses the same 3-phase `TransactionTemplate` pattern (`APPROVED` -> `INITIATED` in Phase A, gateway refund HTTP in Phase B, `SUCCEEDED`/`FAILED` or ambiguous retention in Phase C).
2. **Database-Backed Idempotency & Payload Verification**:
   - `payment_attempts` enforces `UNIQUE (tenant_id, idempotency_key)` and persists `request_hash` (SHA-256 of `tenantId|billId|paymentMethod|providerType|amount|currency`).
   - Identical retries (including concurrent threads racing on the unique constraint) replay the existing payment without issuing duplicate provider orders.
   - Reusing an `Idempotency-Key` with a mutated payload returns `409 Conflict` (`IDEMPOTENCY_KEY_PAYLOAD_MISMATCH`).
3. **Row-Level Locking & Overpayment Prevention**:
   - `RestaurantBillRepository.findByIdAndTenantIdForUpdate` and `RestaurantPaymentRepository.findByIdAndTenantIdForUpdate` serialize concurrent initiations, settlements, webhooks, and cash receipts at the PostgreSQL row level (`SELECT ... FOR UPDATE`).
   - Overpayment is explicitly blocked (`409 OVERPAYMENT_PREVENTED`) whenever a payment's payable amount exceeds the bill's locked `outstandingBalance`.

---

## 4. Operational Failure Recovery Runbook

| Failure Scenario | System Behavior | Recovery Mechanism |
| :--- | :--- | :--- |
| **Provider Read/Connect Timeout** | Phase C sets `PENDING_CONFIRMATION` + `ambiguous_outcome = true` (never `FAILED`). | Webhook resolves automatically via `notes.paymentReference` or `providerOrderId`; otherwise `ScheduledReconciliationJob` polls provider status. |
| **Provider 5xx / Malformed 2xx JSON** | Treated as ambiguous (`PENDING_CONFIRMATION`). | Retained until webhook or reconciliation confirms authoritative provider state. |
| **Provider Status API Unavailable During Reconciliation** | `fetchOrderStatus` sets `transientError = true`; payment stays `PENDING_CONFIRMATION` (never prematurely cancelled). | Automatically retried on the next ShedLock reconciliation cycle. |
| **DB Failure in Phase C After Provider Order Created** | Logs `CRITICAL_RECONCILIATION_ALERT` with `paymentId`, `paymentRef`, and `providerOrderId`. | Early webhook correlates by `notes.paymentReference` and backfills `providerOrderId`; ops can also run `POST /api/v1/payments/reconciliation/run`. |
| **Webhook Arrives Before Phase C Commits** | Webhook resolves payment via `notes.paymentReference`, settles to `SUCCEEDED`, and backfills `providerOrderId`. Phase C sees `isConfirmedOrRefunded == true` and preserves `SUCCEEDED`. | Fully automatic; audit trail records `PROVIDER_RESPONSE_AFTER_CONFIRMATION`. |
| **Delayed `payment.failed` Webhook After `SUCCEEDED`** | Rejected by `PaymentStateMachine.isConfirmedOrRefunded`; webhook stored as `IGNORED`. | No action required. |
