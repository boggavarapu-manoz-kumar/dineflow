package com.dineflow.payment.repository;

import com.dineflow.payment.domain.PaymentReconciliationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentReconciliationLogRepository extends JpaRepository<PaymentReconciliationLog, UUID> {
    List<PaymentReconciliationLog> findByTenantIdOrderByStartedAtDesc(UUID tenantId);
    List<PaymentReconciliationLog> findTop20ByTenantIdOrderByStartedAtDesc(UUID tenantId);
    List<PaymentReconciliationLog> findTop20ByOrderByStartedAtDesc();
}
