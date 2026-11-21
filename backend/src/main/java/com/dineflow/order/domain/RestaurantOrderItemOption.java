package com.dineflow.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "restaurant_order_item_options")
public class RestaurantOrderItemOption {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    private RestaurantOrderItem orderItem;

    @Column(name = "option_id")
    private UUID optionId;

    @Column(name = "option_name_snapshot", nullable = false, length = 128)
    private String optionNameSnapshot;

    @Column(name = "price_contribution", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceContribution = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public RestaurantOrderItemOption() {
    }

    public RestaurantOrderItemOption(RestaurantOrderItem orderItem, UUID optionId, String optionNameSnapshot, BigDecimal priceContribution) {
        this.orderItem = orderItem;
        this.optionId = optionId;
        this.optionNameSnapshot = optionNameSnapshot;
        this.priceContribution = priceContribution;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public RestaurantOrderItem getOrderItem() {
        return orderItem;
    }

    public void setOrderItem(RestaurantOrderItem orderItem) {
        this.orderItem = orderItem;
    }

    public UUID getOptionId() {
        return optionId;
    }

    public void setOptionId(UUID optionId) {
        this.optionId = optionId;
    }

    public String getOptionNameSnapshot() {
        return optionNameSnapshot;
    }

    public void setOptionNameSnapshot(String optionNameSnapshot) {
        this.optionNameSnapshot = optionNameSnapshot;
    }

    public BigDecimal getPriceContribution() {
        return priceContribution;
    }

    public void setPriceContribution(BigDecimal priceContribution) {
        this.priceContribution = priceContribution;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
