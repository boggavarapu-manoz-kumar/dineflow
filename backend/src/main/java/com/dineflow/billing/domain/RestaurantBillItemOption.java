package com.dineflow.billing.domain;

import com.dineflow.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "restaurant_bill_item_options")
public class RestaurantBillItemOption extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bill_item_id", nullable = false)
    private RestaurantBillItem billItem;

    @Column(name = "option_id")
    private UUID optionId;

    @Column(name = "option_name_snapshot", nullable = false, length = 128)
    private String optionNameSnapshot;

    @Column(name = "price_contribution", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceContribution = BigDecimal.ZERO;

    public RestaurantBillItemOption() {
    }

    public RestaurantBillItemOption(RestaurantBillItem billItem, UUID optionId, String optionNameSnapshot, BigDecimal priceContribution) {
        this.billItem = billItem;
        this.optionId = optionId;
        this.optionNameSnapshot = optionNameSnapshot;
        this.priceContribution = priceContribution != null ? priceContribution : BigDecimal.ZERO;
    }

    public RestaurantBillItem getBillItem() { return billItem; }
    public void setBillItem(RestaurantBillItem billItem) { this.billItem = billItem; }

    public UUID getOptionId() { return optionId; }
    public void setOptionId(UUID optionId) { this.optionId = optionId; }

    public String getOptionNameSnapshot() { return optionNameSnapshot; }
    public void setOptionNameSnapshot(String optionNameSnapshot) { this.optionNameSnapshot = optionNameSnapshot; }

    public BigDecimal getPriceContribution() { return priceContribution; }
    public void setPriceContribution(BigDecimal priceContribution) { this.priceContribution = priceContribution; }
}
