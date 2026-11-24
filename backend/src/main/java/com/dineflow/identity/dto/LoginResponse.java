package com.dineflow.identity.dto;

import java.util.List;
import java.util.UUID;

public record LoginResponse(
        String accessToken,
        long expiresIn,
        UserProfileResponse user
) {
    public record UserProfileResponse(
            UUID id,
            String email,
            String fullName,
            String status,
            UUID activeTenantId,
            UUID branchId,
            String role,
            List<String> permissions
    ) {
        public UserProfileResponse(
                UUID id,
                String email,
                String fullName,
                String status,
                UUID activeTenantId,
                String role,
                List<String> permissions) {
            this(id, email, fullName, status, activeTenantId, null, role, permissions);
        }
    }
}