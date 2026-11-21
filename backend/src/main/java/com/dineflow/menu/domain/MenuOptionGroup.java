package com.dineflow.menu.domain;

import com.dineflow.common.domain.TenantAwareEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "menu_option_groups")
public class MenuOptionGroup extends TenantAwareEntity {

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "min_selections", nullable = false)
    private Integer minSelections = 0;

    @Column(name = "max_selections", nullable = false)
    private Integer maxSelections = 1;

    @Column(name = "is_required", nullable = false)
    private Boolean isRequired = false;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @OneToMany(mappedBy = "optionGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<MenuOption> options = new ArrayList<>();

    public MenuOptionGroup() {
    }

    public MenuOptionGroup(String name, String description, Integer minSelections, Integer maxSelections,
                           Boolean isRequired, Integer displayOrder, Boolean isActive) {
        this.name = name;
        this.description = description;
        this.minSelections = minSelections != null ? minSelections : 0;
        this.maxSelections = maxSelections != null ? maxSelections : 1;
        this.isRequired = isRequired != null ? isRequired : false;
        this.displayOrder = displayOrder != null ? displayOrder : 0;
        this.isActive = isActive != null ? isActive : true;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getMinSelections() {
        return minSelections;
    }

    public void setMinSelections(Integer minSelections) {
        this.minSelections = minSelections != null ? minSelections : 0;
    }

    public Integer getMaxSelections() {
        return maxSelections;
    }

    public void setMaxSelections(Integer maxSelections) {
        this.maxSelections = maxSelections != null ? maxSelections : 1;
    }

    public Boolean getIsRequired() {
        return isRequired;
    }

    public void setIsRequired(Boolean isRequired) {
        this.isRequired = isRequired != null ? isRequired : false;
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

    public List<MenuOption> getOptions() {
        return options;
    }

    public void setOptions(List<MenuOption> options) {
        this.options = options;
    }

    public void addOption(MenuOption option) {
        options.add(option);
        option.setOptionGroup(this);
        option.setTenantId(this.getTenantId());
    }

    public void removeOption(MenuOption option) {
        options.remove(option);
        option.setOptionGroup(null);
    }
}
