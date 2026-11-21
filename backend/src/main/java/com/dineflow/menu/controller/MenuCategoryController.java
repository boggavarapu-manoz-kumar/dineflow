package com.dineflow.menu.controller;

import com.dineflow.common.api.ApiResponse;
import com.dineflow.menu.dto.CategoryReorderRequest;
import com.dineflow.menu.dto.CategoryResponse;
import com.dineflow.menu.dto.CategoryStatusUpdateRequest;
import com.dineflow.menu.dto.CreateCategoryRequest;
import com.dineflow.menu.dto.UpdateCategoryRequest;
import com.dineflow.menu.service.MenuCategoryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/menu/categories")
public class MenuCategoryController {

    private final MenuCategoryService categoryService;

    public MenuCategoryController(MenuCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MENU_MANAGE')")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @Valid @RequestBody CreateCategoryRequest request) {
        CategoryResponse response = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Category created successfully"));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('MENU_VIEW', 'MENU_MANAGE')")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> listAllCategories(
            @RequestParam(name = "activeOnly", required = false) Boolean activeOnly) {
        List<CategoryResponse> categories = categoryService.listAllCategories(activeOnly);
        return ResponseEntity.ok(ApiResponse.ok(categories, "Categories retrieved successfully"));
    }

    @GetMapping("/paged")
    @PreAuthorize("hasAnyAuthority('MENU_VIEW', 'MENU_MANAGE')")
    public ResponseEntity<ApiResponse<Page<CategoryResponse>>> listCategoriesPaged(
            @RequestParam(name = "activeOnly", required = false) Boolean activeOnly,
            @PageableDefault(size = 20, sort = "displayOrder", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<CategoryResponse> categories = categoryService.listCategoriesPaged(activeOnly, pageable);
        return ResponseEntity.ok(ApiResponse.ok(categories, "Paged categories retrieved successfully"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('MENU_VIEW', 'MENU_MANAGE')")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategoryById(@PathVariable("id") UUID id) {
        CategoryResponse response = categoryService.getCategoryById(id);
        return ResponseEntity.ok(ApiResponse.ok(response, "Category retrieved successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MENU_MANAGE')")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateCategoryRequest request) {
        CategoryResponse response = categoryService.updateCategory(id, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Category updated successfully"));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('MENU_MANAGE')")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategoryStatus(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CategoryStatusUpdateRequest request) {
        CategoryResponse response = categoryService.updateCategoryStatus(id, request.isActive());
        return ResponseEntity.ok(ApiResponse.ok(response, "Category status updated successfully"));
    }

    @PostMapping("/reorder")
    @PreAuthorize("hasAuthority('MENU_MANAGE')")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> reorderCategories(
            @Valid @RequestBody CategoryReorderRequest request) {
        List<CategoryResponse> reordered = categoryService.reorderCategories(request);
        return ResponseEntity.ok(ApiResponse.ok(reordered, "Categories reordered successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MENU_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable("id") UUID id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Category deleted successfully"));
    }
}
