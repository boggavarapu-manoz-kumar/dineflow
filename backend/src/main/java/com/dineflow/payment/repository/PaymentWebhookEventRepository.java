package com.dineflow.payment.repository;

import com.dineflow.payment.domain.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, UUID> {
    Optional<PaymentWebhookEvent> findByProviderNameAndProviderEventId(String providerName, String providerEventId);
    boolean existsByProviderNameAndProviderEventId(String providerName, String providerEventId);
}
