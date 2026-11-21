package com.dineflow.order.domain;

import com.dineflow.common.domain.TenantAwareEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "restaurant_orders",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_tenant_order_number", columnNames = {"tenant_id", "order_number"})
        }
)
public class RestaurantOrder extends TenantAwareEntity {

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "customer_session_id", nullable = false)
    private UUID customerSessionId;

    @Column(name = "table_number", nullable = false, length = 32)
    private String tableNumber;

    @Column(name = "order_number", nullable = false, length = 64)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private OrderStatus status = OrderStatus.PLACED;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "INR";

    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "tax_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxPercentage = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "service_charge_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal serviceChargePercentage = BigDecimal.ZERO;

    @Column(name = "service_charge_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal serviceChargeAmount = BigDecimal.ZERO;

    @Column(name = "grand_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal grandTotal;

    @Column(name = "total_item_count", nullable = false)
    private Integer totalItemCount = 0;

    @Column(name = "special_notes", length = 512)
    private String specialNotes;

    @Column(name = "cart_id")
    private UUID cartId;

    @Column(name = "assigned_waiter_id")
    private UUID assignedWaiterId;

    @jakarta.persistence.Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<RestaurantOrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<RestaurantOrderStatusHistory> statusHistory = new ArrayList<>();

    public RestaurantOrder() {
    }

    public RestaurantOrder(UUID tenantId, UUID branchId, UUID customerSessionId, String tableNumber, String orderNumber) {
        setTenantId(tenantId);
        this.branchId = branchId;
        this.customerSessionId = customerSessionId;
        this.tableNumber = tableNumber;
        this.orderNumber = orderNumber;
        this.status = OrderStatus.PLACED;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public void setBranchId(UUID branchId) {
        this.branchId = branchId;
    }

    public UUID getCustomerSessionId() {
        return customerSessionId;
    }

    public void setCustomerSessionId(UUID customerSessionId) {
        this.customerSessionId = customerSessionId;
    }

    public String getTableNumber() {
        return tableNumber;
    }

    public void setTableNumber(String tableNumber) {
        this.tableNumber = tableNumber;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getTaxPercentage() {
        return taxPercentage;
    }

    public void setTaxPercentage(BigDecimal taxPercentage) {
        this.taxPercentage = taxPercentage;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public BigDecimal getServiceChargePercentage() {
        return serviceChargePercentage;
    }

    public void setServiceChargePercentage(BigDecimal serviceChargePercentage) {
        this.serviceChargePercentage = serviceChargePercentage;
    }

    public BigDecimal getServiceChargeAmount() {
        return serviceChargeAmount;
    }

    public void setServiceChargeAmount(BigDecimal serviceChargeAmount) {
        this.serviceChargeAmount = serviceChargeAmount;
    }

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    public void setGrandTotal(BigDecimal grandTotal) {
        this.grandTotal = grandTotal;
    }

    public Integer getTotalItemCount() {
        return totalItemCount;
    }

    public void setTotalItemCount(Integer totalItemCount) {
        this.totalItemCount = totalItemCount;
    }

    public String getSpecialNotes() {
        return specialNotes;
    }

    public void setSpecialNotes(String specialNotes) {
        this.specialNotes = specialNotes;
    }

    public UUID getCartId() {
        return cartId;
    }

    public void setCartId(UUID cartId) {
        this.cartId = cartId;
    }

    public UUID getAssignedWaiterId() {
        return assignedWaiterId;
    }

    public void setAssignedWaiterId(UUID assignedWaiterId) {
        this.assignedWaiterId = assignedWaiterId;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public List<RestaurantOrderItem> getItems() {
        return items;
    }

    public void setItems(List<RestaurantOrderItem> items) {
        this.items = items;
    }

    public void addItem(RestaurantOrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public List<RestaurantOrderStatusHistory> getStatusHistory() {
        return statusHistory;
    }

    public void setStatusHistory(List<RestaurantOrderStatusHistory> statusHistory) {
        this.statusHistory = statusHistory;
    }

    public void addStatusHistory(RestaurantOrderStatusHistory history) {
        statusHistory.add(history);
        history.setOrder(this);
    }
}
