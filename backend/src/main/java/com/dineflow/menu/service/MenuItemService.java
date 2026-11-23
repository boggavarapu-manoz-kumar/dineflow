package com.dineflow.menu.service;

import com.dineflow.branch.domain.Branch;
import com.dineflow.branch.repository.BranchRepository;
import com.dineflow.common.exception.BusinessException;
import com.dineflow.common.exception.ResourceNotFoundException;
import com.dineflow.menu.domain.MenuCategory;
import com.dineflow.menu.domain.MenuItem;
import com.dineflow.menu.domain.MenuItemBranchConfiguration;
import com.dineflow.menu.domain.MenuItemImage;
import com.dineflow.menu.domain.MenuOption;
import com.dineflow.menu.domain.MenuOptionGroup;
import com.dineflow.menu.domain.MenuVariant;
import com.dineflow.menu.dto.BranchConfigOverrideRequest;
import com.dineflow.menu.dto.BranchConfigResponse;
import com.dineflow.menu.dto.CreateMenuItemRequest;
import com.dineflow.menu.dto.CreateOptionGroupRequest;
import com.dineflow.menu.dto.CreateOptionRequest;
import com.dineflow.menu.dto.CreateVariantRequest;
import com.dineflow.menu.dto.MenuItemDetailResponse;
import com.dineflow.menu.dto.MenuItemSummaryResponse;
import com.dineflow.menu.dto.UpdateMenuItemRequest;
import com.dineflow.menu.repository.MenuCategoryRepository;
import com.dineflow.menu.repository.MenuItemBranchConfigurationRepository;
import com.dineflow.menu.repository.MenuItemImageRepository;
import com.dineflow.menu.repository.MenuItemRepository;
import com.dineflow.menu.repository.MenuOptionGroupRepository;
import com.dineflow.menu.repository.MenuOptionRepository;
import com.dineflow.menu.repository.MenuVariantRepository;
import com.dineflow.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MenuItemService {

    private static final Logger log = LoggerFactory.getLogger(MenuItemService.class);

    private final MenuItemRepository menuItemRepository;
    private final MenuCategoryRepository categoryRepository;
    private final MenuVariantRepository variantRepository;
    private final MenuOptionGroupRepository optionGroupRepository;
    private final MenuOptionRepository optionRepository;
    private final MenuItemBranchConfigurationRepository branchConfigRepository;
    private final MenuItemImageRepository imageRepository;
    private final BranchRepository branchRepository;
    private final ImageStorageService imageStorageService;
    private final MenuProjectionCacheService menuCacheService;

    public MenuItemService(MenuItemRepository menuItemRepository,
                           MenuCategoryRepository categoryRepository,
                           MenuVariantRepository variantRepository,
                           MenuOptionGroupRepository optionGroupRepository,
                           MenuOptionRepository optionRepository,
                           MenuItemBranchConfigurationRepository branchConfigRepository,
                           MenuItemImageRepository imageRepository,
                           BranchRepository branchRepository,
                           ImageStorageService imageStorageService,
                           MenuProjectionCacheService menuCacheService) {
        this.menuItemRepository = menuItemRepository;
        this.categoryRepository = categoryRepository;
        this.variantRepository = variantRepository;
        this.optionGroupRepository = optionGroupRepository;
        this.optionRepository = optionRepository;
        this.branchConfigRepository = branchConfigRepository;
        this.imageRepository = imageRepository;
        this.branchRepository = branchRepository;
        this.imageStorageService = imageStorageService;
        this.menuCacheService = menuCacheService;
    }

    @Transactional
    public MenuItemDetailResponse createMenuItem(CreateMenuItemRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        String normalized = MenuCategory.normalize(request.name());

        if (menuItemRepository.findByTenantIdAndNormalizedName(tenantId, normalized).isPresent()) {
            throw new BusinessException(
                    "A food item with name '" + request.name().trim() + "' already exists for this tenant",
                    HttpStatus.CONFLICT,
                    "DUPLICATE_ITEM_NAME"
            );
        }

        MenuCategory category = categoryRepository.findByIdAndTenantId(request.categoryId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.categoryId()));

        int displayOrder = request.displayOrder() != null ? request.displayOrder() : 0;
        if (request.displayOrder() == null) {
            List<MenuItem> existing = menuItemRepository.findAllByTenantIdOrderByDisplayOrderAsc(tenantId);
            if (!existing.isEmpty()) {
                displayOrder = existing.get(existing.size() - 1).getDisplayOrder() + 1;
            }
        }

        MenuItem item = new MenuItem(
                category,
                request.name().trim(),
                request.shortDescription(),
                request.detailedDescription(),
                request.basePrice(),
                request.dietaryClassification(),
                request.preparationTimeMinutes(),
                request.isActive() != null ? request.isActive() : true,
                request.isAvailable() != null ? request.isAvailable() : true,
                displayOrder
        );
        item.setTenantId(tenantId);

        MenuItem saved = menuItemRepository.save(item);
        menuCacheService.evictTenantMenu(tenantId);
        log.info("Created food item '{}' (id: {}) for tenant: {}", saved.getName(), saved.getId(), tenantId);
        return MenuItemDetailResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public MenuItemDetailResponse getMenuItemById(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        MenuItem item = menuItemRepository.findByIdAndTenantIdWithDetails(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem", id));
        return MenuItemDetailResponse.fromEntity(item);
    }

    @Transactional(readOnly = true)
    public Page<MenuItemSummaryResponse> listMenuItemsPaged(UUID categoryId, Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();
        Page<MenuItem> page;
        if (categoryId != null) {
            page = menuItemRepository.findAllByTenantIdAndCategoryId(tenantId, categoryId, pageable);
        } else {
            page = menuItemRepository.findAllByTenantId(tenantId, pageable);
        }
        return page.map(MenuItemSummaryResponse::fromEntity);
    }

    @Transactional
    public MenuItemDetailResponse updateMenuItem(UUID id, UpdateMenuItemRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        MenuItem item = menuItemRepository.findByIdAndTenantIdWithDetails(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem", id));

        String normalized = MenuCategory.normalize(request.name());
        if (menuItemRepository.existsByTenantIdAndNormalizedNameExcludingId(tenantId, normalized, id)) {
            throw new BusinessException(
                    "A food item with name '" + request.name().trim() + "' already exists for this tenant",
                    HttpStatus.CONFLICT,
                    "DUPLICATE_ITEM_NAME"
            );
        }

        if (!item.getCategory().getId().equals(request.categoryId())) {
            MenuCategory newCategory = categoryRepository.findByIdAndTenantId(request.categoryId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Category", request.categoryId()));
            item.setCategory(newCategory);
        }

        item.setName(request.name().trim());
        item.setShortDescription(request.shortDescription());
        item.setDetailedDescription(request.detailedDescription());
        item.setBasePrice(request.basePrice());
        if (request.dietaryClassification() != null) {
            item.setDietaryClassification(request.dietaryClassification());
        }
        if (request.preparationTimeMinutes() != null) {
            item.setPreparationTimeMinutes(request.preparationTimeMinutes());
        }
        if (request.isActive() != null) {
            item.setIsActive(request.isActive());
        }
        if (request.isAvailable() != null) {
            item.setIsAvailable(request.isAvailable());
        }
        if (request.displayOrder() != null) {
            item.setDisplayOrder(request.displayOrder());
        }

        MenuItem updated = menuItemRepository.save(item);
        menuCacheService.evictTenantMenu(tenantId);
        log.info("Updated food item '{}' (id: {}) for tenant: {}", updated.getName(), updated.getId(), tenantId);
        return MenuItemDetailResponse.fromEntity(updated);
    }

    @Transactional
    public void deleteMenuItem(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        MenuItem item = menuItemRepository.findByIdAndTenantIdWithDetails(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem", id));

        // Cleanup images from storage
        for (MenuItemImage img : item.getImages()) {
            imageStorageService.deleteImage(img.getStorageKey());
        }

        menuItemRepository.delete(item);
        menuCacheService.evictTenantMenu(tenantId);
        log.info("Deleted food item '{}' (id: {}) for tenant: {}", item.getName(), id, tenantId);
    }

    // ==================== VARIANTS ====================

    @Transactional
    public MenuItemDetailResponse.VariantResponse addVariant(UUID menuItemId, CreateVariantRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        MenuItem item = menuItemRepository.findByIdAndTenantId(menuItemId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem", menuItemId));

        int displayOrder = request.displayOrder() != null ? request.displayOrder() : item.getVariants().size();
        MenuVariant variant = new MenuVariant(
                item,
                request.name().trim(),
                request.description(),
                request.price(),
                displayOrder,
                request.isActive() != null ? request.isActive() : true,
                request.isAvailable() != null ? request.isAvailable() : true
        );
        variant.setTenantId(tenantId);
        item.getVariants().add(variant);

        MenuVariant saved = variantRepository.save(variant);
        log.info("Added variant '{}' to food item '{}' for tenant: {}", saved.getName(), item.getName(), tenantId);
        return MenuItemDetailResponse.VariantResponse.fromEntity(saved);
    }

    @Transactional
    public void deleteVariant(UUID menuItemId, UUID variantId) {
        UUID tenantId = TenantContext.requireTenantId();
        MenuVariant variant = variantRepository.findByIdAndTenantId(variantId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("MenuVariant", variantId));

        if (!variant.getMenuItem().getId().equals(menuItemId)) {
            throw new BusinessException("Variant does not belong to specified menu item", HttpStatus.BAD_REQUEST, "INVALID_VARIANT_ASSOCIATION");
        }

        variantRepository.delete(variant);
        log.info("Deleted variant (id: {}) from food item (id: {})", variantId, menuItemId);
    }

    // ==================== OPTION GROUPS & OPTIONS ====================

    @Transactional
    public MenuItemDetailResponse.OptionGroupResponse createOptionGroup(CreateOptionGroupRequest request) {
        UUID tenantId = TenantContext.requireTenantId();

        if (request.maxSelections() < request.minSelections()) {
            throw new BusinessException("maxSelections cannot be less than minSelections", HttpStatus.BAD_REQUEST, "INVALID_SELECTION_RANGE");
        }

        MenuOptionGroup group = new MenuOptionGroup(
                request.name().trim(),
                request.description(),
                request.minSelections(),
                request.maxSelections(),
                request.isRequired() != null ? request.isRequired() : false,
                request.displayOrder() != null ? request.displayOrder() : 0,
                request.isActive() != null ? request.isActive() : true
        );
        group.setTenantId(tenantId);

        MenuOptionGroup saved = optionGroupRepository.save(group);
        log.info("Created option group '{}' (id: {}) for tenant: {}", saved.getName(), saved.getId(), tenantId);
        return MenuItemDetailResponse.OptionGroupResponse.fromEntity(saved);
    }

    @Transactional
    public MenuItemDetailResponse.OptionResponse addOptionToGroup(UUID groupId, CreateOptionRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        MenuOptionGroup group = optionGroupRepository.findByIdAndTenantId(groupId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("MenuOptionGroup", groupId));

        int displayOrder = request.displayOrder() != null ? request.displayOrder() : group.getOptions().size();
        MenuOption option = new MenuOption(
                group,
                request.name().trim(),
                request.description(),
                request.additionalPrice(),
                displayOrder,
                request.isActive() != null ? request.isActive() : true,
                request.isAvailable() != null ? request.isAvailable() : true
        );
        option.setTenantId(tenantId);

        MenuOption saved = optionRepository.save(option);
        log.info("Added option '{}' to group '{}' for tenant: {}", saved.getName(), group.getName(), tenantId);
        return MenuItemDetailResponse.OptionResponse.fromEntity(saved);
    }

    @Transactional
    public void linkOptionGroupToItem(UUID menuItemId, UUID groupId) {
        UUID tenantId = TenantContext.requireTenantId();
        MenuItem item = menuItemRepository.findByIdAndTenantIdWithDetails(menuItemId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem", menuItemId));

        MenuOptionGroup group = optionGroupRepository.findByIdAndTenantId(groupId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("MenuOptionGroup", groupId));

        item.getOptionGroups().add(group);
        menuItemRepository.save(item);
        log.info("Linked option group '{}' to item '{}' for tenant: {}", group.getName(), item.getName(), tenantId);
    }

    // ==================== BRANCH CONFIGURATION OVERRIDES ====================

    @Transactional
    public BranchConfigResponse setBranchOverride(UUID branchId, UUID menuItemId, BranchConfigOverrideRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        Branch branch = branchRepository.findByIdAndTenantId(branchId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", branchId));

        MenuItem item = menuItemRepository.findByIdAndTenantId(menuItemId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem", menuItemId));

        MenuItemBranchConfiguration config = branchConfigRepository
                .findByBranchIdAndMenuItemIdAndTenantId(branchId, menuItemId, tenantId)
                .orElseGet(() -> {
                    MenuItemBranchConfiguration newConfig = new MenuItemBranchConfiguration();
                    newConfig.setBranch(branch);
                    newConfig.setMenuItem(item);
                    newConfig.setTenantId(tenantId);
                    return newConfig;
                });

        config.setPriceOverride(request.priceOverride());
        config.setIsAvailable(request.isAvailable());
        if (request.isVisible() != null) {
            config.setIsVisible(request.isVisible());
        }

        MenuItemBranchConfiguration saved = branchConfigRepository.save(config);
        menuCacheService.evictTenantMenu(tenantId);
        log.info("Updated branch override for item '{}' at branch '{}' for tenant: {}", item.getName(), branch.getName(), tenantId);
        return BranchConfigResponse.fromEntity(saved);
    }

    // ==================== IMAGES ====================

    @Transactional
    public MenuItemDetailResponse.ImageResponse uploadItemImage(UUID menuItemId, MultipartFile file, Boolean isPrimary) {
        UUID tenantId = TenantContext.requireTenantId();
        MenuItem item = menuItemRepository.findByIdAndTenantIdWithDetails(menuItemId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem", menuItemId));

        ImageStorageService.StoredImageResult stored = imageStorageService.storeImage(tenantId, menuItemId, file);

        boolean primary = Boolean.TRUE.equals(isPrimary) || item.getImages().isEmpty();
        if (primary && !item.getImages().isEmpty()) {
            for (MenuItemImage existing : item.getImages()) {
                existing.setIsPrimary(false);
            }
        }

        MenuItemImage img = new MenuItemImage(
                item,
                stored.imageUrl(),
                stored.storageKey(),
                stored.originalFileName(),
                stored.fileSizeBytes(),
                stored.contentType(),
                item.getImages().size(),
                primary
        );
        img.setTenantId(tenantId);

        MenuItemImage saved = imageRepository.save(img);
        menuCacheService.evictTenantMenu(tenantId);
        log.info("Uploaded image '{}' for item '{}' for tenant: {}", saved.getFileName(), item.getName(), tenantId);
        return MenuItemDetailResponse.ImageResponse.fromEntity(saved);
    }
}
