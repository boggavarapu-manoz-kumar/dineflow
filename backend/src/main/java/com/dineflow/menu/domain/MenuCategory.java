package com.dineflow.menu.domain;

import com.dineflow.common.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "menu_categories",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_categories_tenant_normalized_name", columnNames = {"tenant_id", "normalized_name"})
        }
)
public class MenuCategory extends TenantAwareEntity {

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 128)
    private String normalized_name;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "icon_name", length = 64)
    private String iconName;

    @Column(name = "image_url", length = 1024)
    private String imageUrl;

    public MenuCategory() {
    }

    public MenuCategory(String name, String description, Integer displayOrder, Boolean isActive, String iconName, String imageUrl) {
        this.name = name;
        this.normalized_name = normalize(name);
        this.description = description;
        this.displayOrder = displayOrder != null ? displayOrder : 0;
        this.isActive = isActive != null ? isActive : true;
        this.iconName = iconName;
        this.imageUrl = imageUrl;
    }

    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        return input.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.normalized_name = normalize(name);
    }

    public String getNormalizedName() {
        return normalized_name;
    }

    public void setNormalizedName(String normalized_name) {
        this.normalized_name = normalized_name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder != null ? displayOrder : 0;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive != null ? isActive : true;
    }

    public String getIconName() {
        return iconName;
    }

    public void setIconName(String iconName) {
        this.iconName = iconName;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
