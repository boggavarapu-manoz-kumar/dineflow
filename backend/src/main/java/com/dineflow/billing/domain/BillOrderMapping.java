package com.dineflow.billing.domain;

import com.dineflow.common.domain.BaseEntity;
import com.dineflow.order.domain.RestaurantOrder;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "bill_order_mappings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_bill_order", columnNames = {"bill_id", "order_id"})
        }
)
public class BillOrderMapping extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bill_id", nullable = false)
    private RestaurantBill bill;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private RestaurantOrder order;

    @Column(name = "order_number", nullable = false, length = 64)
    private String orderNumber;

    public BillOrderMapping() {
    }

    public BillOrderMapping(RestaurantBill bill, RestaurantOrder order, String orderNumber) {
        this.bill = bill;
        this.order = order;
        this.orderNumber = orderNumber;
    }

    public RestaurantBill getBill() { return bill; }
    public void setBill(RestaurantBill bill) { this.bill = bill; }

    public RestaurantOrder getOrder() { return order; }
    public void setOrder(RestaurantOrder order) { this.order = order; }

    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
}
