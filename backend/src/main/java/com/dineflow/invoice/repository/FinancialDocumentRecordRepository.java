package com.dineflow.invoice.repository;

import com.dineflow.invoice.domain.FinancialDocumentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FinancialDocumentRecordRepository extends JpaRepository<FinancialDocumentRecord, UUID> {

    Optional<FinancialDocumentRecord> findByEntityIdAndDocumentTypeAndTenantId(UUID entityId, String documentType, UUID tenantId);

    Optional<FinancialDocumentRecord> findByStorageKeyAndTenantId(String storageKey, UUID tenantId);
}
