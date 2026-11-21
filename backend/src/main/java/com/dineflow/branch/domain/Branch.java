package com.dineflow.branch.domain;

import com.dineflow.common.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "branches",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_branches_tenant_code", columnNames = {"tenant_id", "code"})
        }
)
public class Branch extends TenantAwareEntity {

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "is_main", nullable = false)
    private Boolean isMain = false;

    public Branch() {
    }

    public Branch(String name, String code, String address, String phone, Boolean isActive, Boolean isMain) {
        this.name = name;
        this.code = code;
        this.address = address;
        this.phone = phone;
        this.isActive = isActive != null ? isActive : true;
        this.isMain = isMain != null ? isMain : false;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive != null ? isActive : true;
    }

    public Boolean getIsMain() {
        return isMain;
    }

    public void setIsMain(Boolean isMain) {
        this.isMain = isMain != null ? isMain : false;
    }
}
