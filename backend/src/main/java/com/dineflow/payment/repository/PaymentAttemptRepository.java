package com.dineflow.payment.repository;

import com.dineflow.payment.domain.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    Optional<PaymentAttempt> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    Optional<PaymentAttempt> findByTenantIdAndProviderOrderId(UUID tenantId, String providerOrderId);

    Optional<PaymentAttempt> findFirstByProviderOrderId(String providerOrderId);

    Optional<PaymentAttempt> findFirstByProviderTransactionId(String providerTransactionId);
}
