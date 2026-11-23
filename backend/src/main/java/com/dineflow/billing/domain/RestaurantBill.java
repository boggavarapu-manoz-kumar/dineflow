package com.dineflow.billing.domain;

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
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "restaurant_bills",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_tenant_bill_number", columnNames = {"tenant_id", "bill_number"})
        }
)
public class RestaurantBill extends TenantAwareEntity {

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "customer_session_id")
    private UUID customerSessionId;

    @Column(name = "bill_number", nullable = false, length = 64)
    private String billNumber;

    @Column(name = "table_number", nullable = false, length = 32)
    private String tableNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private BillStatus status = BillStatus.ISSUED;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "INR";

    @Column(name = "restaurant_name_snapshot", nullable = false, length = 128)
    private String restaurantNameSnapshot;

    @Column(name = "branch_name_snapshot", length = 128)
    private String branchNameSnapshot;

    @Column(name = "branch_address_snapshot", length = 255)
    private String branchAddressSnapshot;

    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

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

    @Column(name = "paid_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "outstanding_balance", nullable = false, precision = 10, scale = 2)
    private BigDecimal outstandingBalance;

    @Column(name = "void_reason", length = 255)
    private String voidReason;

    @Column(name = "voided_by_user_id")
    private UUID voidedByUserId;

    @Column(name = "issued_by_user_id")
    private UUID issuedByUserId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<BillOrderMapping> orderMappings = new ArrayList<>();

    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<RestaurantBillItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<RestaurantBillStatusHistory> statusHistory = new ArrayList<>();

    public RestaurantBill() {
    }

    public RestaurantBill(UUID tenantId, UUID branchId, UUID customerSessionId, String billNumber, String tableNumber, String restaurantNameSnapshot) {
        setTenantId(tenantId);
        this.branchId = branchId;
        this.customerSessionId = customerSessionId;
        this.billNumber = billNumber;
        this.tableNumber = tableNumber;
        this.restaurantNameSnapshot = restaurantNameSnapshot;
        this.status = BillStatus.ISSUED;
        this.issuedAt = Instant.now();
    }

    public void addOrderMapping(BillOrderMapping mapping) {
        orderMappings.add(mapping);
        mapping.setBill(this);
    }

    public void addItem(RestaurantBillItem item) {
        items.add(item);
        item.setBill(this);
    }

    public void addStatusHistory(RestaurantBillStatusHistory history) {
        statusHistory.add(history);
        history.setBill(this);
    }

    // Getters and Setters
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }

    public UUID getCustomerSessionId() { return customerSessionId; }
    public void setCustomerSessionId(UUID customerSessionId) { this.customerSessionId = customerSessionId; }

    public String getBillNumber() { return billNumber; }
    public void setBillNumber(String billNumber) { this.billNumber = billNumber; }

    public String getTableNumber() { return tableNumber; }
    public void setTableNumber(String tableNumber) { this.tableNumber = tableNumber; }

    public BillStatus getStatus() { return status; }
    public void setStatus(BillStatus status) { this.status = status; }

    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }

    public String getRestaurantNameSnapshot() { return restaurantNameSnapshot; }
    public void setRestaurantNameSnapshot(String restaurantNameSnapshot) { this.restaurantNameSnapshot = restaurantNameSnapshot; }

    public String getBranchNameSnapshot() { return branchNameSnapshot; }
    public void setBranchNameSnapshot(String branchNameSnapshot) { this.branchNameSnapshot = branchNameSnapshot; }

    public String getBranchAddressSnapshot() { return branchAddressSnapshot; }
    public void setBranchAddressSnapshot(String branchAddressSnapshot) { this.branchAddressSnapshot = branchAddressSnapshot; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }

    public BigDecimal getTaxPercentage() { return taxPercentage; }
    public void setTaxPercentage(BigDecimal taxPercentage) { this.taxPercentage = taxPercentage; }

    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }

    public BigDecimal getServiceChargePercentage() { return serviceChargePercentage; }
    public void setServiceChargePercentage(BigDecimal serviceChargePercentage) { this.serviceChargePercentage = serviceChargePercentage; }

    public BigDecimal getServiceChargeAmount() { return serviceChargeAmount; }
    public void setServiceChargeAmount(BigDecimal serviceChargeAmount) { this.serviceChargeAmount = serviceChargeAmount; }

    public BigDecimal getGrandTotal() { return grandTotal; }
    public void setGrandTotal(BigDecimal grandTotal) { this.grandTotal = grandTotal; }

    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }

    public BigDecimal getOutstandingBalance() { return outstandingBalance; }
    public void setOutstandingBalance(BigDecimal outstandingBalance) { this.outstandingBalance = outstandingBalance; }

    public String getVoidReason() { return voidReason; }
    public void setVoidReason(String voidReason) { this.voidReason = voidReason; }

    public UUID getVoidedByUserId() { return voidedByUserId; }
    public void setVoidedByUserId(UUID voidedByUserId) { this.voidedByUserId = voidedByUserId; }

    public UUID getIssuedByUserId() { return issuedByUserId; }
    public void setIssuedByUserId(UUID issuedByUserId) { this.issuedByUserId = issuedByUserId; }

    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }

    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }

    public Instant getVoidedAt() { return voidedAt; }
    public void setVoidedAt(Instant voidedAt) { this.voidedAt = voidedAt; }

    public List<BillOrderMapping> getOrderMappings() { return orderMappings; }
    public void setOrderMappings(List<BillOrderMapping> orderMappings) { this.orderMappings = orderMappings; }

    public List<RestaurantBillItem> getItems() { return items; }
    public void setItems(List<RestaurantBillItem> items) { this.items = items; }

    public List<RestaurantBillStatusHistory> getStatusHistory() { return statusHistory; }
    public void setStatusHistory(List<RestaurantBillStatusHistory> statusHistory) { this.statusHistory = statusHistory; }
}
