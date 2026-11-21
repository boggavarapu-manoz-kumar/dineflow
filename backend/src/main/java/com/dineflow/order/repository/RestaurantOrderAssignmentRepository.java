package com.dineflow.order.repository;

import com.dineflow.order.domain.RestaurantOrderAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RestaurantOrderAssignmentRepository extends JpaRepository<RestaurantOrderAssignment, UUID> {

    Optional<RestaurantOrderAssignment> findByOrderIdAndAssignmentStatus(UUID orderId, String assignmentStatus);

    List<RestaurantOrderAssignment> findAllByOrderIdOrderByCreatedAtDesc(UUID orderId);

    List<RestaurantOrderAssignment> findAllByTenantIdAndWaiterIdAndAssignmentStatusOrderByCreatedAtDesc(
            UUID tenantId, UUID waiterId, String assignmentStatus);
}
