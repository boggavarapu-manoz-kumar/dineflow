package com.dineflow.order.repository;

import com.dineflow.order.domain.RestaurantOrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RestaurantOrderStatusHistoryRepository extends JpaRepository<RestaurantOrderStatusHistory, UUID> {

    List<RestaurantOrderStatusHistory> findAllByOrderIdOrderByCreatedAtAsc(UUID orderId);
}
