package com.dineflow.cart.repository;

import com.dineflow.cart.domain.CustomerCart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerCartRepository extends JpaRepository<CustomerCart, UUID> {

    Optional<CustomerCart> findByCustomerSessionIdAndTenantId(UUID customerSessionId, UUID tenantId);

    @Query("SELECT c FROM CustomerCart c " +
           "LEFT JOIN FETCH c.items i " +
           "LEFT JOIN FETCH i.menuItem " +
           "LEFT JOIN FETCH i.variant " +
           "LEFT JOIN FETCH i.selectedOptions " +
           "WHERE c.customerSessionId = :sessionId AND c.tenantId = :tenantId")
    Optional<CustomerCart> findBySessionIdWithDetails(@Param("sessionId") UUID sessionId, @Param("tenantId") UUID tenantId);
}
