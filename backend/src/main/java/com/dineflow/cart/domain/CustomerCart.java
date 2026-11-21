package com.dineflow.cart.domain;

import com.dineflow.common.domain.TenantAwareEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "customer_carts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_customer_carts_session", columnNames = {"customer_session_id"})
        }
)
public class CustomerCart extends TenantAwareEntity {

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "customer_session_id", nullable = false)
    private UUID customerSessionId;

    @Column(name = "table_number", nullable = false, length = 32)
    private String tableNumber;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<CustomerCartItem> items = new ArrayList<>();

    public CustomerCart() {
    }

    public CustomerCart(UUID tenantId, UUID branchId, UUID customerSessionId, String tableNumber) {
        setTenantId(tenantId);
        this.branchId = branchId;
        this.customerSessionId = customerSessionId;
        this.tableNumber = tableNumber;
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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public List<CustomerCartItem> getItems() {
        return items;
    }

    public void setItems(List<CustomerCartItem> items) {
        this.items = items;
    }

    public void addItem(CustomerCartItem item) {
        items.add(item);
        item.setCart(this);
    }

    public void removeItem(CustomerCartItem item) {
        items.remove(item);
        item.setCart(null);
    }

    public void clearItems() {
        for (CustomerCartItem item : new ArrayList<>(items)) {
            removeItem(item);
        }
    }
}
