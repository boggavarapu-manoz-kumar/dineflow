package com.dineflow.order.repository;

import com.dineflow.order.domain.OrderIdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderIdempotencyRepository extends JpaRepository<OrderIdempotencyRecord, UUID> {

    Optional<OrderIdempotencyRecord> findByCustomerSessionIdAndIdempotencyKey(UUID customerSessionId, String idempotencyKey);
}
