package com.dineflow.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class DineFlowPrincipal implements UserDetails {

    private final UUID userId;
    private final UUID tenantId;
    private final String roleCode;
    private final String type; // STAFF or GUEST
    private final List<SimpleGrantedAuthority> authorities;

    public DineFlowPrincipal(UUID userId, UUID tenantId, String roleCode, List<String> permissions, String type) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.roleCode = roleCode;
        this.type = type;
        this.authorities = permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public String getType() {
        return type;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return userId.toString();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}