package com.dineflow.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
        @NotBlank(message = "Category name is required")
        @Size(min = 2, max = 128, message = "Category name must be between 2 and 128 characters")
        String name,

        @Size(max = 512, message = "Description cannot exceed 512 characters")
        String description,

        Integer displayOrder,

        Boolean isActive,

        @Size(max = 64, message = "Icon name cannot exceed 64 characters")
        String iconName,

        @Size(max = 1024, message = "Image URL cannot exceed 1024 characters")
        String imageUrl
) {}
