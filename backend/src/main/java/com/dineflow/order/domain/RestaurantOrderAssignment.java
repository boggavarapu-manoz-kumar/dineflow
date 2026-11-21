package com.dineflow.order.domain;

import com.dineflow.common.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "restaurant_order_assignments")
public class RestaurantOrderAssignment extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private RestaurantOrder order;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "waiter_id")
    private UUID waiterId;

    @Column(name = "assigned_by_user_id", nullable = false)
    private UUID assignedByUserId;

    @Column(name = "assignment_status", nullable = false, length = 32)
    private String assignmentStatus = "ACTIVE"; // ACTIVE, REASSIGNED, UNASSIGNED

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "unassigned_at")
    private Instant unassignedAt;

    public RestaurantOrderAssignment() {
    }

    public RestaurantOrderAssignment(RestaurantOrder order, UUID tenantId, UUID branchId, UUID waiterId, UUID assignedByUserId, String reason) {
        this.order = order;
        setTenantId(tenantId);
        this.branchId = branchId;
        this.waiterId = waiterId;
        this.assignedByUserId = assignedByUserId;
        this.assignmentStatus = "ACTIVE";
        this.reason = reason;
    }

    public RestaurantOrder getOrder() {
        return order;
    }

    public void setOrder(RestaurantOrder order) {
        this.order = order;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public void setBranchId(UUID branchId) {
        this.branchId = branchId;
    }

    public UUID getWaiterId() {
        return waiterId;
    }

    public void setWaiterId(UUID waiterId) {
        this.waiterId = waiterId;
    }

    public UUID getAssignedByUserId() {
        return assignedByUserId;
    }

    public void setAssignedByUserId(UUID assignedByUserId) {
        this.assignedByUserId = assignedByUserId;
    }

    public String getAssignmentStatus() {
        return assignmentStatus;
    }

    public void setAssignmentStatus(String assignmentStatus) {
        this.assignmentStatus = assignmentStatus;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getUnassignedAt() {
        return unassignedAt;
    }

    public void setUnassignedAt(Instant unassignedAt) {
        this.unassignedAt = unassignedAt;
    }
}
