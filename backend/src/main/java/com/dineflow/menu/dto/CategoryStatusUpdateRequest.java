package com.dineflow.menu.dto;

import jakarta.validation.constraints.NotNull;

public record CategoryStatusUpdateRequest(
        @NotNull(message = "Active status must not be null")
        Boolean isActive
) {}
