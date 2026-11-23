package com.dineflow.billing.domain;

import com.dineflow.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "restaurant_bill_status_history")
public class RestaurantBillStatusHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bill_id", nullable = false)
    private RestaurantBill bill;

    @Column(name = "previous_status", length = 32)
    private String previousStatus;

    @Column(name = "new_status", nullable = false, length = 32)
    private String newStatus;

    @Column(name = "changed_by_user_id")
    private UUID changedByUserId;

    @Column(name = "changed_by_role", nullable = false, length = 64)
    private String changedByRole;

    @Column(name = "actor_type", nullable = false, length = 32)
    private String actorType = "STAFF";

    @Column(name = "reason", length = 255)
    private String reason;

    public RestaurantBillStatusHistory() {
    }

    public RestaurantBillStatusHistory(RestaurantBill bill, String previousStatus, String newStatus,
                                       UUID changedByUserId, String changedByRole, String actorType, String reason) {
        this.bill = bill;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.changedByUserId = changedByUserId;
        this.changedByRole = changedByRole;
        this.actorType = actorType != null ? actorType : "STAFF";
        this.reason = reason;
    }

    public RestaurantBill getBill() { return bill; }
    public void setBill(RestaurantBill bill) { this.bill = bill; }

    public String getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(String previousStatus) { this.previousStatus = previousStatus; }

    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }

    public UUID getChangedByUserId() { return changedByUserId; }
    public void setChangedByUserId(UUID changedByUserId) { this.changedByUserId = changedByUserId; }

    public String getChangedByRole() { return changedByRole; }
    public void setChangedByRole(String changedByRole) { this.changedByRole = changedByRole; }

    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
