package com.dineflow.menu.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CategoryReorderRequest(
        @NotEmpty(message = "Category order list must not be empty")
        List<@NotNull(message = "Category ID cannot be null") UUID> categoryIds
) {}
