package com.dineflow.payment.repository;

import com.dineflow.payment.domain.PaymentMethod;
import com.dineflow.payment.domain.PaymentStatus;
import com.dineflow.payment.domain.RestaurantPayment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RestaurantPaymentRepository extends JpaRepository<RestaurantPayment, UUID> {

    Optional<RestaurantPayment> findByIdAndTenantId(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM RestaurantPayment p WHERE p.id = :id AND p.tenantId = :tenantId")
    Optional<RestaurantPayment> findByIdAndTenantIdForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    Optional<RestaurantPayment> findByPaymentReference(String paymentReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM RestaurantPayment p WHERE p.paymentReference = :paymentReference")
    Optional<RestaurantPayment> findByPaymentReferenceForUpdate(@Param("paymentReference") String paymentReference);

    Optional<RestaurantPayment> findByTenantIdAndPaymentReference(UUID tenantId, String paymentReference);

    List<RestaurantPayment> findByTenantIdAndPaymentStatusIn(UUID tenantId, Collection<PaymentStatus> statuses);

    List<RestaurantPayment> findByPaymentStatusIn(Collection<PaymentStatus> statuses);

    @Query("""
            SELECT p FROM RestaurantPayment p
            WHERE (:tenantId IS NULL OR p.tenantId = :tenantId)
              AND p.paymentStatus IN :statuses
            ORDER BY p.createdAt ASC
            """)
    Page<RestaurantPayment> findReconciliationBatch(@Param("tenantId") UUID tenantId,
                                                    @Param("statuses") Collection<PaymentStatus> statuses,
                                                    Pageable pageable);

    List<RestaurantPayment> findAllByBillIdAndTenantIdOrderByCreatedAtDesc(UUID billId, UUID tenantId);

    List<RestaurantPayment> findAllByTenantIdAndBranchIdAndPaymentStatusOrderByCreatedAtDesc(UUID tenantId, UUID branchId, PaymentStatus status);

    Optional<RestaurantPayment> findByTenantIdAndProviderPaymentId(UUID tenantId, String providerPaymentId);

    List<RestaurantPayment> findByTenantIdAndPaymentStatus(UUID tenantId, PaymentStatus paymentStatus);

    List<RestaurantPayment> findAllByTenantIdAndBranchIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            UUID tenantId, UUID branchId, Instant start, Instant end);

    List<RestaurantPayment> findAllByTenantIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            UUID tenantId, Instant start, Instant end);

    List<RestaurantPayment> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<RestaurantPayment> findAllByTenantIdAndBranchIdOrderByCreatedAtDesc(UUID tenantId, UUID branchId);

    long countByTenantIdAndPaymentStatusAndCreatedAtBefore(UUID tenantId, PaymentStatus status, Instant cutoff);

    long countByPaymentStatus(PaymentStatus status);

    /**
     * Confirmed payment collections grouped by PaymentMethod.
     * Strictly excludes CREATED, PENDING, INITIATED, PROCESSING, FAILED, CANCELLED.
     * Returns rows of Object[]: [paymentMethod, txnCount, confirmedPaidAmount]
     */
    @Query("""
            SELECT p.paymentMethod,
                   COUNT(p),
                   COALESCE(SUM(p.paidAmount), 0)
            FROM RestaurantPayment p
            WHERE p.tenantId = :tenantId
              AND (:branchId IS NULL OR p.branchId = :branchId)
              AND p.paymentStatus IN ('SUCCEEDED', 'PARTIALLY_REFUNDED', 'REFUNDED')
              AND p.settledAt >= :start
              AND p.settledAt < :end
            GROUP BY p.paymentMethod
            """)
    List<Object[]> aggregateConfirmedPaymentsByMethod(@Param("tenantId") UUID tenantId,
                                                      @Param("branchId") UUID branchId,
                                                      @Param("start") Instant start,
                                                      @Param("end") Instant end);

    /**
     * Confirmed payment collections grouped by branchId.
     * Returns rows of Object[]: [branchId, confirmedPaidAmount]
     */
    @Query("""
            SELECT p.branchId,
                   COALESCE(SUM(p.paidAmount), 0)
            FROM RestaurantPayment p
            WHERE p.tenantId = :tenantId
              AND (:branchId IS NULL OR p.branchId = :branchId)
              AND p.paymentStatus IN ('SUCCEEDED', 'PARTIALLY_REFUNDED', 'REFUNDED')
              AND p.settledAt >= :start
              AND p.settledAt < :end
            GROUP BY p.branchId
            """)
    List<Object[]> aggregateConfirmedPaymentsByBranch(@Param("tenantId") UUID tenantId,
                                                      @Param("branchId") UUID branchId,
                                                      @Param("start") Instant start,
                                                      @Param("end") Instant end);

    /**
     * Paginated confirmed payment query for memory-safe streaming CSV export with optional method filter.
     */
    @Query("""
            SELECT p FROM RestaurantPayment p
            WHERE p.tenantId = :tenantId
              AND (:branchId IS NULL OR p.branchId = :branchId)
              AND (:paymentMethod IS NULL OR p.paymentMethod = :paymentMethod)
              AND p.paymentStatus IN ('SUCCEEDED', 'PARTIALLY_REFUNDED', 'REFUNDED')
              AND p.settledAt >= :start
              AND p.settledAt < :end
            ORDER BY p.settledAt ASC
            """)
    Page<RestaurantPayment> findConfirmedPageForExport(@Param("tenantId") UUID tenantId,
                                                       @Param("branchId") UUID branchId,
                                                       @Param("paymentMethod") PaymentMethod paymentMethod,
                                                       @Param("start") Instant start,
                                                       @Param("end") Instant end,
                                                       Pageable pageable);
}
