package com.dineflow.invoice.repository;

import com.dineflow.invoice.domain.RestaurantInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RestaurantInvoiceRepository extends JpaRepository<RestaurantInvoice, UUID> {

    Optional<RestaurantInvoice> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<RestaurantInvoice> findByTenantIdAndInvoiceNumber(UUID tenantId, String invoiceNumber);

    Optional<RestaurantInvoice> findByBillIdAndTenantId(UUID billId, UUID tenantId);

    List<RestaurantInvoice> findAllByTenantIdAndBranchIdOrderByIssuedAtDesc(UUID tenantId, UUID branchId);

    List<RestaurantInvoice> findAllByTenantIdOrderByIssuedAtDesc(UUID tenantId);
}
