package com.dineflow.menu.domain;

import com.dineflow.common.domain.TenantAwareEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(
        name = "menu_items",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_menu_items_tenant_normalized_name", columnNames = {"tenant_id", "normalized_name"})
        }
)
public class MenuItem extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private MenuCategory category;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 128)
    private String normalizedName;

    @Column(name = "short_description", length = 255)
    private String shortDescription;

    @Column(name = "detailed_description", columnDefinition = "TEXT")
    private String detailedDescription;

    @Column(name = "base_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "dietary_classification", nullable = false, length = 32)
    private DietaryClassification dietaryClassification = DietaryClassification.VEGETARIAN;

    @Column(name = "preparation_time_minutes")
    private Integer preparationTimeMinutes = 15;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "is_available", nullable = false)
    private Boolean isAvailable = true;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @OneToMany(mappedBy = "menuItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private Set<MenuItemImage> images = new HashSet<>();

    @OneToMany(mappedBy = "menuItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private Set<MenuVariant> variants = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "menu_item_option_groups",
            joinColumns = @JoinColumn(name = "menu_item_id"),
            inverseJoinColumns = @JoinColumn(name = "option_group_id")
    )
    @OrderBy("displayOrder ASC")
    private Set<MenuOptionGroup> optionGroups = new HashSet<>();

    public MenuItem() {
    }

    public MenuItem(MenuCategory category, String name, String shortDescription, String detailedDescription,
                    BigDecimal basePrice, DietaryClassification dietaryClassification,
                    Integer preparationTimeMinutes, Boolean isActive, Boolean isAvailable, Integer displayOrder) {
        this.category = category;
        this.name = name;
        this.normalizedName = MenuCategory.normalize(name);
        this.shortDescription = shortDescription;
        this.detailedDescription = detailedDescription;
        this.basePrice = basePrice;
        this.dietaryClassification = dietaryClassification != null ? dietaryClassification : DietaryClassification.VEGETARIAN;
        this.preparationTimeMinutes = preparationTimeMinutes != null ? preparationTimeMinutes : 15;
        this.isActive = isActive != null ? isActive : true;
        this.isAvailable = isAvailable != null ? isAvailable : true;
        this.displayOrder = displayOrder != null ? displayOrder : 0;
    }

    public MenuCategory getCategory() {
        return category;
    }

    public void setCategory(MenuCategory category) {
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.normalizedName = MenuCategory.normalize(name);
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public void setNormalizedName(String normalizedName) {
        this.normalizedName = normalizedName;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }

    public String getDetailedDescription() {
        return detailedDescription;
    }

    public void setDetailedDescription(String detailedDescription) {
        this.detailedDescription = detailedDescription;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public DietaryClassification getDietaryClassification() {
        return dietaryClassification;
    }

    public void setDietaryClassification(DietaryClassification dietaryClassification) {
        this.dietaryClassification = dietaryClassification != null ? dietaryClassification : DietaryClassification.VEGETARIAN;
    }

    public Integer getPreparationTimeMinutes() {
        return preparationTimeMinutes;
    }

    public void setPreparationTimeMinutes(Integer preparationTimeMinutes) {
        this.preparationTimeMinutes = preparationTimeMinutes;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive != null ? isActive : true;
    }

    public Boolean getIsAvailable() {
        return isAvailable;
    }

    public void setIsAvailable(Boolean isAvailable) {
        this.isAvailable = isAvailable != null ? isAvailable : true;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder != null ? displayOrder : 0;
    }

    public Set<MenuItemImage> getImages() {
        return images;
    }

    public void setImages(Set<MenuItemImage> images) {
        this.images = images;
    }

    public Set<MenuVariant> getVariants() {
        return variants;
    }

    public void setVariants(Set<MenuVariant> variants) {
        this.variants = variants;
    }

    public Set<MenuOptionGroup> getOptionGroups() {
        return optionGroups;
    }

    public void setOptionGroups(Set<MenuOptionGroup> optionGroups) {
        this.optionGroups = optionGroups;
    }

    public void addVariant(MenuVariant variant) {
        variants.add(variant);
        variant.setMenuItem(this);
        variant.setTenantId(this.getTenantId());
    }

    public void removeVariant(MenuVariant variant) {
        variants.remove(variant);
        variant.setMenuItem(null);
    }

    public void addImage(MenuItemImage image) {
        images.add(image);
        image.setMenuItem(this);
        image.setTenantId(this.getTenantId());
    }

    public void removeImage(MenuItemImage image) {
        images.remove(image);
        image.setMenuItem(null);
    }
}
