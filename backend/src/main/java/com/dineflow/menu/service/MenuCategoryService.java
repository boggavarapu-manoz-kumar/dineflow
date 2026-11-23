package com.dineflow.menu.service;

import com.dineflow.common.exception.BusinessException;
import com.dineflow.common.exception.ResourceNotFoundException;
import com.dineflow.menu.domain.MenuCategory;
import com.dineflow.menu.dto.CategoryReorderRequest;
import com.dineflow.menu.dto.CategoryResponse;
import com.dineflow.menu.dto.CreateCategoryRequest;
import com.dineflow.menu.dto.UpdateCategoryRequest;
import com.dineflow.menu.repository.MenuCategoryRepository;
import com.dineflow.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MenuCategoryService {

    private static final Logger log = LoggerFactory.getLogger(MenuCategoryService.class);

    private final MenuCategoryRepository categoryRepository;
    private final com.dineflow.menu.repository.MenuItemRepository menuItemRepository;
    private final MenuProjectionCacheService menuCacheService;

    public MenuCategoryService(MenuCategoryRepository categoryRepository,
                               com.dineflow.menu.repository.MenuItemRepository menuItemRepository,
                               MenuProjectionCacheService menuCacheService) {
        this.categoryRepository = categoryRepository;
        this.menuItemRepository = menuItemRepository;
        this.menuCacheService = menuCacheService;
    }

    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        String normalizedName = MenuCategory.normalize(request.name());

        if (categoryRepository.findByTenantIdAndNormalizedName(tenantId, normalizedName).isPresent()) {
            throw new BusinessException(
                    "A category with name '" + request.name().trim() + "' already exists for this tenant",
                    HttpStatus.CONFLICT,
                    "DUPLICATE_CATEGORY_NAME"
            );
        }

        int displayOrder = request.displayOrder() != null ? request.displayOrder() : 0;
        if (request.displayOrder() == null) {
            // Place at the end of existing categories by default
            List<MenuCategory> existing = categoryRepository.findAllByTenantIdOrderByDisplayOrderAsc(tenantId);
            if (!existing.isEmpty()) {
                displayOrder = existing.get(existing.size() - 1).getDisplayOrder() + 1;
            }
        }

        MenuCategory category = new MenuCategory(
                request.name().trim(),
                request.description(),
                displayOrder,
                request.isActive() != null ? request.isActive() : true,
                request.iconName(),
                request.imageUrl()
        );
        category.setTenantId(tenantId);

        MenuCategory saved = categoryRepository.save(category);
        menuCacheService.evictTenantMenu(tenantId);
        log.info("Created category '{}' (id: {}) for tenant: {}", saved.getName(), saved.getId(), tenantId);
        return CategoryResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listAllCategories(Boolean activeOnly) {
        UUID tenantId = TenantContext.requireTenantId();
        List<MenuCategory> list;
        if (Boolean.TRUE.equals(activeOnly)) {
            list = categoryRepository.findAllByTenantIdAndIsActiveTrueOrderByDisplayOrderAsc(tenantId);
        } else {
            list = categoryRepository.findAllByTenantIdOrderByDisplayOrderAsc(tenantId);
        }
        return list.stream().map(CategoryResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public Page<CategoryResponse> listCategoriesPaged(Boolean activeOnly, Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();
        Page<MenuCategory> page;
        if (activeOnly != null) {
            page = categoryRepository.findAllByTenantIdAndIsActive(tenantId, activeOnly, pageable);
        } else {
            page = categoryRepository.findAllByTenantId(tenantId, pageable);
        }
        return page.map(CategoryResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(UUID categoryId) {
        UUID tenantId = TenantContext.requireTenantId();
        MenuCategory category = categoryRepository.findByIdAndTenantId(categoryId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
        return CategoryResponse.fromEntity(category);
    }

    @Transactional
    public CategoryResponse updateCategory(UUID categoryId, UpdateCategoryRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        MenuCategory category = categoryRepository.findByIdAndTenantId(categoryId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));

        String normalizedName = MenuCategory.normalize(request.name());
        if (categoryRepository.existsByTenantIdAndNormalizedNameExcludingId(tenantId, normalizedName, categoryId)) {
            throw new BusinessException(
                    "A category with name '" + request.name().trim() + "' already exists for this tenant",
                    HttpStatus.CONFLICT,
                    "DUPLICATE_CATEGORY_NAME"
            );
        }

        category.setName(request.name().trim());
        category.setDescription(request.description());
        if (request.displayOrder() != null) {
            category.setDisplayOrder(request.displayOrder());
        }
        if (request.isActive() != null) {
            category.setIsActive(request.isActive());
        }
        category.setIconName(request.iconName());
        category.setImageUrl(request.imageUrl());

        MenuCategory updated = categoryRepository.save(category);
        menuCacheService.evictTenantMenu(tenantId);
        log.info("Updated category '{}' (id: {}) for tenant: {}", updated.getName(), updated.getId(), tenantId);
        return CategoryResponse.fromEntity(updated);
    }

    @Transactional
    public CategoryResponse updateCategoryStatus(UUID categoryId, boolean isActive) {
        UUID tenantId = TenantContext.requireTenantId();
        MenuCategory category = categoryRepository.findByIdAndTenantId(categoryId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));

        category.setIsActive(isActive);
        MenuCategory updated = categoryRepository.save(category);
        menuCacheService.evictTenantMenu(tenantId);
        log.info("Category '{}' status changed to active={} for tenant: {}", updated.getName(), isActive, tenantId);
        return CategoryResponse.fromEntity(updated);
    }

    @Transactional
    public List<CategoryResponse> reorderCategories(CategoryReorderRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        List<UUID> orderIds = request.categoryIds();

        for (int i = 0; i < orderIds.size(); i++) {
            UUID categoryId = orderIds.get(i);
            Optional<MenuCategory> opt = categoryRepository.findByIdAndTenantId(categoryId, tenantId);
            if (opt.isPresent()) {
                MenuCategory category = opt.get();
                category.setDisplayOrder(i);
                categoryRepository.save(category);
            } else {
                throw new ResourceNotFoundException("Category", categoryId);
            }
        }

        menuCacheService.evictTenantMenu(tenantId);
        return categoryRepository.findAllByTenantIdOrderByDisplayOrderAsc(tenantId)
                .stream()
                .map(CategoryResponse::fromEntity)
                .toList();
    }

    @Transactional
    public void deleteCategory(UUID categoryId) {
        UUID tenantId = TenantContext.requireTenantId();
        MenuCategory category = categoryRepository.findByIdAndTenantId(categoryId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));

        long linkedItemCount = menuItemRepository.countByCategoryIdAndTenantId(categoryId, tenantId);
        if (linkedItemCount > 0) {
            throw new BusinessException(
                    "Cannot delete category that contains " + linkedItemCount + " food item(s). Reassign or delete items first.",
                    HttpStatus.CONFLICT,
                    "CATEGORY_HAS_ITEMS"
            );
        }

        categoryRepository.delete(category);
        menuCacheService.evictTenantMenu(tenantId);
        log.info("Deleted category '{}' (id: {}) for tenant: {}", category.getName(), categoryId, tenantId);
    }
}
