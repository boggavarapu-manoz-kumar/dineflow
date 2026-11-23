package com.dineflow.order.repository;

import com.dineflow.order.domain.OrderStatus;
import com.dineflow.order.domain.RestaurantOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RestaurantOrderRepository extends JpaRepository<RestaurantOrder, UUID> {

    long countByStatusIn(Collection<OrderStatus> statuses);

    Optional<RestaurantOrder> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<RestaurantOrder> findByTenantIdAndOrderNumber(UUID tenantId, String orderNumber);

    List<RestaurantOrder> findAllByCustomerSessionIdAndTenantIdOrderByCreatedAtDesc(UUID customerSessionId, UUID tenantId);

    List<RestaurantOrder> findAllByTenantIdAndAssignedWaiterIdOrderByCreatedAtDesc(UUID tenantId, UUID assignedWaiterId);

    List<RestaurantOrder> findAllByTenantIdAndBranchIdAndAssignedWaiterIdOrderByCreatedAtDesc(UUID tenantId, UUID branchId, UUID assignedWaiterId);

    List<RestaurantOrder> findAllByTenantIdAndBranchIdOrderByCreatedAtDesc(UUID tenantId, UUID branchId);

    List<RestaurantOrder> findAllByTenantIdAndStatusInOrderByCreatedAtAsc(UUID tenantId, List<com.dineflow.order.domain.OrderStatus> statuses);

    List<RestaurantOrder> findAllByTenantIdAndBranchIdAndStatusInOrderByCreatedAtAsc(UUID tenantId, UUID branchId, List<com.dineflow.order.domain.OrderStatus> statuses);

    Page<RestaurantOrder> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT COUNT(o) FROM RestaurantOrder o WHERE o.tenantId = :tenantId AND o.createdAt >= :since")
    long countOrdersSince(@Param("tenantId") UUID tenantId, @Param("since") Instant since);

    /**
     * Aggregates total orders and cancelled orders in a UTC window.
     * Returns Object[]: [totalOrderCount, totalOrderValue, cancelledOrderCount, cancelledOrderValue]
     */
    @Query("""
            SELECT COUNT(o),
                   COALESCE(SUM(o.grandTotal), 0),
                   SUM(CASE WHEN o.status = 'CANCELLED' THEN 1 ELSE 0 END),
                   COALESCE(SUM(CASE WHEN o.status = 'CANCELLED' THEN o.grandTotal ELSE 0 END), 0)
            FROM RestaurantOrder o
            WHERE o.tenantId = :tenantId
              AND (:branchId IS NULL OR o.branchId = :branchId)
              AND o.createdAt >= :start
              AND o.createdAt < :end
            """)
    Object[] aggregateOrderStats(@Param("tenantId") UUID tenantId,
                                 @Param("branchId") UUID branchId,
                                 @Param("start") Instant start,
                                 @Param("end") Instant end);
}
