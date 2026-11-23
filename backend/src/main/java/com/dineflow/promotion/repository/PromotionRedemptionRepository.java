package com.dineflow.promotion.repository;

import com.dineflow.promotion.domain.PromotionRedemption;
import com.dineflow.promotion.domain.RedemptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PromotionRedemptionRepository extends JpaRepository<PromotionRedemption, UUID> {

    List<PromotionRedemption> findAllByBillIdAndTenantIdAndStatus(UUID billId, UUID tenantId, RedemptionStatus status);

    List<PromotionRedemption> findAllByPromotionIdAndTenantIdOrderByRedeemedAtDesc(UUID promotionId, UUID tenantId);

    List<PromotionRedemption> findAllByTenantIdOrderByRedeemedAtDesc(UUID tenantId);

    boolean existsByBillIdAndPromotionIdAndTenantIdAndStatus(UUID billId, UUID promotionId, UUID tenantId, RedemptionStatus status);

    long countByPromotionIdAndTenantIdAndCustomerSessionIdAndStatus(UUID promotionId, UUID tenantId, UUID customerSessionId, RedemptionStatus status);
}
