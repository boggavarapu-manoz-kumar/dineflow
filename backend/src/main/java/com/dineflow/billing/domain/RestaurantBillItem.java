package com.dineflow.billing.domain;

import com.dineflow.common.domain.BaseEntity;
import com.dineflow.order.domain.RestaurantOrder;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "restaurant_bill_items")
public class RestaurantBillItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bill_id", nullable = false)
    private RestaurantBill bill;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private RestaurantOrder order;

    @Column(name = "order_item_id")
    private UUID orderItemId;

    @Column(name = "item_name_snapshot", nullable = false, length = 128)
    private String itemNameSnapshot;

    @Column(name = "variant_name_snapshot", length = 64)
    private String variantNameSnapshot;

    @Column(name = "item_description_snapshot", length = 1024)
    private String itemDescriptionSnapshot;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "base_unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseUnitPrice;

    @Column(name = "options_unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal optionsUnitPrice = BigDecimal.ZERO;

    @Column(name = "effective_unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal effectiveUnitPrice;

    @Column(name = "line_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "special_instructions", length = 255)
    private String specialInstructions;

    @OneToMany(mappedBy = "billItem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<RestaurantBillItemOption> options = new ArrayList<>();

    public RestaurantBillItem() {
    }

    public RestaurantBillItem(RestaurantBill bill, RestaurantOrder order, UUID orderItemId,
                              String itemNameSnapshot, String variantNameSnapshot, String itemDescriptionSnapshot,
                              Integer quantity, BigDecimal baseUnitPrice, BigDecimal optionsUnitPrice,
                              BigDecimal effectiveUnitPrice, BigDecimal lineTotal, String specialInstructions) {
        this.bill = bill;
        this.order = order;
        this.orderItemId = orderItemId;
        this.itemNameSnapshot = itemNameSnapshot;
        this.variantNameSnapshot = variantNameSnapshot;
        this.itemDescriptionSnapshot = itemDescriptionSnapshot;
        this.quantity = quantity;
        this.baseUnitPrice = baseUnitPrice;
        this.optionsUnitPrice = optionsUnitPrice != null ? optionsUnitPrice : BigDecimal.ZERO;
        this.effectiveUnitPrice = effectiveUnitPrice;
        this.lineTotal = lineTotal;
        this.specialInstructions = specialInstructions;
    }

    public void addOption(RestaurantBillItemOption option) {
        options.add(option);
        option.setBillItem(this);
    }

    // Getters and Setters
    public RestaurantBill getBill() { return bill; }
    public void setBill(RestaurantBill bill) { this.bill = bill; }

    public RestaurantOrder getOrder() { return order; }
    public void setOrder(RestaurantOrder order) { this.order = order; }

    public UUID getOrderItemId() { return orderItemId; }
    public void setOrderItemId(UUID orderItemId) { this.orderItemId = orderItemId; }

    public String getItemNameSnapshot() { return itemNameSnapshot; }
    public void setItemNameSnapshot(String itemNameSnapshot) { this.itemNameSnapshot = itemNameSnapshot; }

    public String getVariantNameSnapshot() { return variantNameSnapshot; }
    public void setVariantNameSnapshot(String variantNameSnapshot) { this.variantNameSnapshot = variantNameSnapshot; }

    public String getItemDescriptionSnapshot() { return itemDescriptionSnapshot; }
    public void setItemDescriptionSnapshot(String itemDescriptionSnapshot) { this.itemDescriptionSnapshot = itemDescriptionSnapshot; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getBaseUnitPrice() { return baseUnitPrice; }
    public void setBaseUnitPrice(BigDecimal baseUnitPrice) { this.baseUnitPrice = baseUnitPrice; }

    public BigDecimal getOptionsUnitPrice() { return optionsUnitPrice; }
    public void setOptionsUnitPrice(BigDecimal optionsUnitPrice) { this.optionsUnitPrice = optionsUnitPrice; }

    public BigDecimal getEffectiveUnitPrice() { return effectiveUnitPrice; }
    public void setEffectiveUnitPrice(BigDecimal effectiveUnitPrice) { this.effectiveUnitPrice = effectiveUnitPrice; }

    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }

    public String getSpecialInstructions() { return specialInstructions; }
    public void setSpecialInstructions(String specialInstructions) { this.specialInstructions = specialInstructions; }

    public List<RestaurantBillItemOption> getOptions() { return options; }
    public void setOptions(List<RestaurantBillItemOption> options) { this.options = options; }
}
