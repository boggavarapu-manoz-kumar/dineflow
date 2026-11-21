package com.dineflow.menu.controller;

import com.dineflow.common.api.ApiResponse;
import com.dineflow.menu.dto.BranchConfigOverrideRequest;
import com.dineflow.menu.dto.BranchConfigResponse;
import com.dineflow.menu.dto.CreateMenuItemRequest;
import com.dineflow.menu.dto.CreateOptionGroupRequest;
import com.dineflow.menu.dto.CreateOptionRequest;
import com.dineflow.menu.dto.CreateVariantRequest;
import com.dineflow.menu.dto.MenuItemDetailResponse;
import com.dineflow.menu.dto.MenuItemSummaryResponse;
import com.dineflow.menu.dto.UpdateMenuItemRequest;
import com.dineflow.menu.service.MenuItemService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/menu/items")
public class MenuItemController {

    private final MenuItemService menuItemService;

    public MenuItemController(MenuItemService menuItemService) {
        this.menuItemService = menuItemService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MENU_MANAGE')")
    public ResponseEntity<ApiResponse<MenuItemDetailResponse>> createMenuItem(
            @Valid @RequestBody CreateMenuItemRequest request) {
        MenuItemDetailResponse response = menuItemService.createMenuItem(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Menu item created successfully"));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('MENU_VIEW', 'MENU_MANAGE')")
    public ResponseEntity<ApiResponse<Page<MenuItemSummaryResponse>>> listMenuItems(
            @RequestParam(name = "categoryId", required = false) UUID categoryId,
            @PageableDefault(size = 20, sort = "displayOrder", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<MenuItemSummaryResponse> page = menuItemService.listMenuItemsPaged(categoryId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page, "Menu items retrieved successfully"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('MENU_VIEW', 'MENU_MANAGE')")
    public ResponseEntity<ApiResponse<MenuItemDetailResponse>> getMenuItemById(@PathVariable("id") UUID id) {
        MenuItemDetailResponse response = menuItemService.getMenuItemById(id);
        return ResponseEntity.ok(ApiResponse.ok(response, "Menu item retrieved successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MENU_MANAGE')")
    public ResponseEntity<ApiResponse<MenuItemDetailResponse>> updateMenuItem(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateMenuItemRequest request) {
        MenuItemDetailResponse response = menuItemService.updateMenuItem(id, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Menu item updated successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MENU_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deleteMenuItem(@PathVariable("id") UUID id) {
        menuItemService.deleteMenuItem(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Menu item deleted successfully"));
    }

    // ==================== VARIANTS ====================

    @PostMapping("/{id}/variants")
    @PreAuthorize("hasAuthority('MENU_MANAGE')")
    public ResponseEntity<ApiResponse<MenuItemDetailResponse.VariantResponse>> addVariant(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CreateVariantRequest request) {
        MenuItemDetailResponse.VariantResponse response = menuItemService.addVariant(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Variant added successfully"));
    }

    @DeleteMapping("/{id}/variants/{variantId}")
    @PreAuthorize("hasAuthority('MENU_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deleteVariant(
            @PathVariable("id") UUID id,
            @PathVariable("variantId") UUID variantId) {
        menuItemService.deleteVariant(id, variantId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Variant deleted successfully"));
    }

    // ==================== OPTION GROUPS ====================

    @PostMapping("/option-groups")
    @PreAuthorize("hasAuthority('MENU_MANAGE')")
    public ResponseEntity<ApiResponse<MenuItemDetailResponse.OptionGroupResponse>> createOptionGroup(
            @Valid @RequestBody CreateOptionGroupRequest request) {
        MenuItemDetailResponse.OptionGroupResponse response = menuItemService.createOptionGroup(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Option group created successfully"));
    }

    @PostMapping("/option-groups/{groupId}/options")
    @PreAuthorize("hasAuthority('MENU_MANAGE')")
    public ResponseEntity<ApiResponse<MenuItemDetailResponse.OptionResponse>> addOptionToGroup(
            @PathVariable("groupId") UUID groupId,
            @Valid @RequestBody CreateOptionRequest request) {
        MenuItemDetailResponse.OptionResponse response = menuItemService.addOptionToGroup(groupId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Option added to group successfully"));
    }

    @PostMapping("/{id}/option-groups/{groupId}")
    @PreAuthorize("hasAuthority('MENU_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> linkOptionGroupToItem(
            @PathVariable("id") UUID id,
            @PathVariable("groupId") UUID groupId) {
        menuItemService.linkOptionGroupToItem(id, groupId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Option group linked to item successfully"));
    }

    // ==================== BRANCH OVERRIDES ====================

    @PutMapping("/{id}/branches/{branchId}")
    @PreAuthorize("hasAuthority('MENU_MANAGE')")
    public ResponseEntity<ApiResponse<BranchConfigResponse>> setBranchOverride(
            @PathVariable("id") UUID id,
            @PathVariable("branchId") UUID branchId,
            @Valid @RequestBody BranchConfigOverrideRequest request) {
        BranchConfigResponse response = menuItemService.setBranchOverride(branchId, id, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Branch configuration updated successfully"));
    }

    // ==================== IMAGES ====================

    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('MENU_MANAGE')")
    public ResponseEntity<ApiResponse<MenuItemDetailResponse.ImageResponse>> uploadItemImage(
            @PathVariable("id") UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "isPrimary", required = false) Boolean isPrimary) {
        MenuItemDetailResponse.ImageResponse response = menuItemService.uploadItemImage(id, file, isPrimary);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Image uploaded successfully"));
    }
}
