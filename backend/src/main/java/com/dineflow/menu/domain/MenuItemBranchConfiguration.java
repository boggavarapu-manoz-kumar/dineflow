package com.dineflow.menu.domain;

import com.dineflow.branch.domain.Branch;
import com.dineflow.common.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

@Entity
@Table(
        name = "menu_item_branch_configs",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_branch_menu_item", columnNames = {"branch_id", "menu_item_id"})
        }
)
public class MenuItemBranchConfiguration extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @Column(name = "price_override", precision = 10, scale = 2)
    private BigDecimal priceOverride;

    @Column(name = "is_available", nullable = false)
    private Boolean isAvailable = true;

    @Column(name = "is_visible", nullable = false)
    private Boolean isVisible = true;

    public MenuItemBranchConfiguration() {
    }

    public MenuItemBranchConfiguration(Branch branch, MenuItem menuItem, BigDecimal priceOverride,
                                       Boolean isAvailable, Boolean isVisible) {
        this.branch = branch;
        this.menuItem = menuItem;
        this.priceOverride = priceOverride;
        this.isAvailable = isAvailable != null ? isAvailable : true;
        this.isVisible = isVisible != null ? isVisible : true;
    }

    public Branch getBranch() {
        return branch;
    }

    public void setBranch(Branch branch) {
        this.branch = branch;
    }

    public MenuItem getMenuItem() {
        return menuItem;
    }

    public void setMenuItem(MenuItem menuItem) {
        this.menuItem = menuItem;
    }

    public BigDecimal getPriceOverride() {
        return priceOverride;
    }

    public void setPriceOverride(BigDecimal priceOverride) {
        this.priceOverride = priceOverride;
    }

    public Boolean getIsAvailable() {
        return isAvailable;
    }

    public void setIsAvailable(Boolean isAvailable) {
        this.isAvailable = isAvailable != null ? isAvailable : true;
    }

    public Boolean getIsVisible() {
        return isVisible;
    }

    public void setIsVisible(Boolean isVisible) {
        this.isVisible = isVisible != null ? isVisible : true;
    }
}
