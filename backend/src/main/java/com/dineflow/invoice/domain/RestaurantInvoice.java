package com.dineflow.invoice.domain;

import com.dineflow.billing.domain.RestaurantBill;
import com.dineflow.common.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "restaurant_invoices",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_tenant_invoice_number", columnNames = {"tenant_id", "invoice_number"})
        }
)
public class RestaurantInvoice extends TenantAwareEntity {

    @Column(name = "branch_id")
    private UUID branchId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bill_id", nullable = false)
    private RestaurantBill bill;

    @Column(name = "invoice_number", nullable = false, length = 64)
    private String invoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private InvoiceStatus status = InvoiceStatus.ISSUED;

    @Column(name = "customer_name", length = 128)
    private String customerName;

    @Column(name = "customer_tax_id", length = 64)
    private String customerTaxId;

    @Column(name = "customer_phone", length = 32)
    private String customerPhone;

    @Column(name = "restaurant_name_snapshot", nullable = false, length = 128)
    private String restaurantNameSnapshot;

    @Column(name = "tax_registration_number", length = 64)
    private String taxRegistrationNumber;

    @Column(name = "branch_name_snapshot", length = 128)
    private String branchNameSnapshot;

    @Column(name = "branch_address_snapshot", length = 255)
    private String branchAddressSnapshot;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "INR";

    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "taxable_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal taxableAmount;

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
    private BigDecimal paidAmount;

    @Column(name = "payment_method", nullable = false, length = 32)
    private String paymentMethod = "PAID_COUNTER";

    @Column(name = "payment_reference", length = 128)
    private String paymentReference;

    @Column(name = "notes", length = 512)
    private String notes;

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    @Column(name = "cancelled_by_user_id")
    private UUID cancelledByUserId;

    @Column(name = "issued_by_user_id")
    private UUID issuedByUserId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    public RestaurantInvoice() {
    }

    public RestaurantInvoice(UUID tenantId, UUID branchId, RestaurantBill bill, String invoiceNumber, String restaurantNameSnapshot) {
        setTenantId(tenantId);
        this.branchId = branchId;
        this.bill = bill;
        this.invoiceNumber = invoiceNumber;
        this.restaurantNameSnapshot = restaurantNameSnapshot;
        this.status = InvoiceStatus.ISSUED;
        this.issuedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }

    public RestaurantBill getBill() { return bill; }
    public void setBill(RestaurantBill bill) { this.bill = bill; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public InvoiceStatus getStatus() { return status; }
    public void setStatus(InvoiceStatus status) { this.status = status; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerTaxId() { return customerTaxId; }
    public void setCustomerTaxId(String customerTaxId) { this.customerTaxId = customerTaxId; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    public String getRestaurantNameSnapshot() { return restaurantNameSnapshot; }
    public void setRestaurantNameSnapshot(String restaurantNameSnapshot) { this.restaurantNameSnapshot = restaurantNameSnapshot; }

    public String getTaxRegistrationNumber() { return taxRegistrationNumber; }
    public void setTaxRegistrationNumber(String taxRegistrationNumber) { this.taxRegistrationNumber = taxRegistrationNumber; }

    public String getBranchNameSnapshot() { return branchNameSnapshot; }
    public void setBranchNameSnapshot(String branchNameSnapshot) { this.branchNameSnapshot = branchNameSnapshot; }

    public String getBranchAddressSnapshot() { return branchAddressSnapshot; }
    public void setBranchAddressSnapshot(String branchAddressSnapshot) { this.branchAddressSnapshot = branchAddressSnapshot; }

    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }

    public BigDecimal getTaxableAmount() { return taxableAmount; }
    public void setTaxableAmount(BigDecimal taxableAmount) { this.taxableAmount = taxableAmount; }

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

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }

    public UUID getCancelledByUserId() { return cancelledByUserId; }
    public void setCancelledByUserId(UUID cancelledByUserId) { this.cancelledByUserId = cancelledByUserId; }

    public UUID getIssuedByUserId() { return issuedByUserId; }
    public void setIssuedByUserId(UUID issuedByUserId) { this.issuedByUserId = issuedByUserId; }

    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }

    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
}
