package com.dineflow.menu.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateOptionGroupRequest(
        @NotBlank(message = "Option group name is required")
        @Size(min = 2, max = 128, message = "Option group name must be between 2 and 128 characters")
        String name,

        @Size(max = 255, message = "Description cannot exceed 255 characters")
        String description,

        @NotNull(message = "Minimum selections is required")
        @Min(value = 0, message = "Minimum selections cannot be negative")
        Integer minSelections,

        @NotNull(message = "Maximum selections is required")
        @Min(value = 1, message = "Maximum selections must be at least 1")
        Integer maxSelections,

        Boolean isRequired,

        Integer displayOrder,

        Boolean isActive
) {}
