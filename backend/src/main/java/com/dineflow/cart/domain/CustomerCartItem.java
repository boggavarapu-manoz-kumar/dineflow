package com.dineflow.cart.domain;

import com.dineflow.common.domain.BaseEntity;
import com.dineflow.menu.domain.MenuItem;
import com.dineflow.menu.domain.MenuOption;
import com.dineflow.menu.domain.MenuVariant;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "customer_cart_items")
public class CustomerCartItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private CustomerCart cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private MenuVariant variant;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "special_instructions", length = 255)
    private String specialInstructions;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "customer_cart_item_options",
            joinColumns = @JoinColumn(name = "cart_item_id"),
            inverseJoinColumns = @JoinColumn(name = "option_id")
    )
    private Set<MenuOption> selectedOptions = new HashSet<>();

    public CustomerCartItem() {
    }

    public CustomerCartItem(CustomerCart cart, MenuItem menuItem, MenuVariant variant, Integer quantity, String specialInstructions) {
        this.cart = cart;
        this.menuItem = menuItem;
        this.variant = variant;
        this.quantity = quantity;
        this.specialInstructions = specialInstructions;
    }

    public CustomerCart getCart() {
        return cart;
    }

    public void setCart(CustomerCart cart) {
        this.cart = cart;
    }

    public MenuItem getMenuItem() {
        return menuItem;
    }

    public void setMenuItem(MenuItem menuItem) {
        this.menuItem = menuItem;
    }

    public MenuVariant getVariant() {
        return variant;
    }

    public void setVariant(MenuVariant variant) {
        this.variant = variant;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getSpecialInstructions() {
        return specialInstructions;
    }

    public void setSpecialInstructions(String specialInstructions) {
        this.specialInstructions = specialInstructions;
    }

    public Set<MenuOption> getSelectedOptions() {
        return selectedOptions;
    }

    public void setSelectedOptions(Set<MenuOption> selectedOptions) {
        this.selectedOptions = selectedOptions;
    }
}
