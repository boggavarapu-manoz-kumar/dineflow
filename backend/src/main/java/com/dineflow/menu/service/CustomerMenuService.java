package com.dineflow.menu.service;

import com.dineflow.identity.domain.CustomerSession;
import com.dineflow.identity.repository.CustomerSessionRepository;
import com.dineflow.menu.domain.MenuCategory;
import com.dineflow.menu.domain.MenuItem;
import com.dineflow.menu.domain.MenuItemBranchConfiguration;
import com.dineflow.menu.domain.MenuItemImage;
import com.dineflow.menu.dto.CustomerMenuResponse;
import com.dineflow.menu.repository.MenuCategoryRepository;
import com.dineflow.menu.repository.MenuItemBranchConfigurationRepository;
import com.dineflow.menu.repository.MenuItemRepository;
import com.dineflow.security.DineFlowPrincipal;
import com.dineflow.tenant.domain.HotelProfile;
import com.dineflow.tenant.domain.Tenant;
import com.dineflow.tenant.repository.HotelProfileRepository;
import com.dineflow.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class CustomerMenuService {

    private final MenuCategoryRepository categoryRepository;
    private final MenuItemRepository menuItemRepository;
    private final MenuItemBranchConfigurationRepository branchConfigRepository;
    private final CustomerSessionRepository customerSessionRepository;
    private final HotelProfileRepository hotelProfileRepository;
    private final TenantRepository tenantRepository;

    public CustomerMenuService(MenuCategoryRepository categoryRepository,
                               MenuItemRepository menuItemRepository,
                               MenuItemBranchConfigurationRepository branchConfigRepository,
                               CustomerSessionRepository customerSessionRepository,
                               HotelProfileRepository hotelProfileRepository,
                               TenantRepository tenantRepository) {
        this.categoryRepository = categoryRepository;
        this.menuItemRepository = menuItemRepository;
        this.branchConfigRepository = branchConfigRepository;
        this.customerSessionRepository = customerSessionRepository;
        this.hotelProfileRepository = hotelProfileRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional(readOnly = true)
    public CustomerMenuResponse getMenuForGuest(DineFlowPrincipal principal) {
        UUID tenantId = principal.getTenantId();
        UUID sessionId = principal.getUserId();

        // Resolve guest session for table number and branch
        CustomerSession session = customerSessionRepository.findById(sessionId).orElse(null);
        String tableNumber = session != null ? session.getTableNumber() : "01";
        UUID branchId = session != null && session.getBranchId() != null 
                ? session.getBranchId() 
                : principal.getBranchId();

        // Load hotel profile for restaurant name, currency, tax — fall back to safe defaults
        Optional<HotelProfile> profileOpt = hotelProfileRepository.findByTenantId(tenantId);
        String restaurantName = tenantRepository.findById(tenantId)
                .map(Tenant::getBusinessName)
                .orElse("Restaurant");
        String currencyCode = "INR";
        BigDecimal taxPercentage = BigDecimal.ZERO;
        BigDecimal serviceChargePercentage = BigDecimal.ZERO;
        if (profileOpt.isPresent()) {
            HotelProfile profile = profileOpt.get();
            if (profile.getCurrencyCode() != null) currencyCode = profile.getCurrencyCode();
            if (profile.getTaxPercentage() != null) taxPercentage = profile.getTaxPercentage();
            if (profile.getServiceChargePercentage() != null) serviceChargePercentage = profile.getServiceChargePercentage();
        }

        // Load active categories ordered by displayOrder
        List<MenuCategory> categories = categoryRepository
                .findAllByTenantIdAndIsActiveTrueOrderByDisplayOrderAsc(tenantId);

        // Load only active menu items for the tenant
        List<MenuItem> allItems = menuItemRepository
                .findAllByTenantIdAndIsActiveTrueOrderByDisplayOrderAsc(tenantId);

        // Load branch-level overrides in bulk (single query) if branch context is known
        Map<UUID, MenuItemBranchConfiguration> branchConfigMap = Collections.emptyMap();
        if (branchId != null) {
            List<MenuItemBranchConfiguration> configs = branchConfigRepository
                    .findAllByBranchIdAndTenantId(branchId, tenantId);
            branchConfigMap = new java.util.HashMap<>();
            for (MenuItemBranchConfiguration config : configs) {
                if (config.getMenuItem() != null) {
                    branchConfigMap.put(config.getMenuItem().getId(), config);
                }
            }
        }

        List<CustomerMenuResponse.CustomerCategoryResponse> categoryResponses = new ArrayList<>();

        for (MenuCategory cat : categories) {
            final UUID catId = cat.getId();
            final Map<UUID, MenuItemBranchConfiguration> finalBranchConfigMap = branchConfigMap;

            List<MenuItem> catItems = allItems.stream()
                    .filter(i -> i.getCategory().getId().equals(catId))
                    .toList();

            List<CustomerMenuResponse.CustomerMenuItemResponse> itemResponses = new ArrayList<>();

            for (MenuItem item : catItems) {
                // Branch config may hide or override availability/price
                MenuItemBranchConfiguration branchConfig = finalBranchConfigMap.get(item.getId());
                boolean branchVisible = branchConfig == null || Boolean.TRUE.equals(branchConfig.getIsVisible());
                if (!branchVisible) continue; // item hidden at this branch

                boolean itemAvailable = Boolean.TRUE.equals(item.getIsAvailable());
                boolean branchPriceOverridden = false;
                BigDecimal effectivePrice = item.getBasePrice();

                if (branchConfig != null) {
                    // Branch availability overrides item-level availability
                    if (!Boolean.TRUE.equals(branchConfig.getIsAvailable())) {
                        itemAvailable = false;
                    }
                    // Apply branch price override if set
                    if (branchConfig.getPriceOverride() != null) {
                        effectivePrice = branchConfig.getPriceOverride();
                        branchPriceOverridden = true;
                    }
                }

                // Resolve images
                String primaryImg = null;
                List<String> allImages = new ArrayList<>();
                if (item.getImages() != null) {
                    for (MenuItemImage img : item.getImages()) {
                        allImages.add(img.getImageUrl());
                        if (Boolean.TRUE.equals(img.getIsPrimary()) && primaryImg == null) {
                            primaryImg = img.getImageUrl();
                        }
                    }
                    if (primaryImg == null && !allImages.isEmpty()) {
                        primaryImg = allImages.get(0);
                    }
                }

                // Resolve variants (active only)
                List<CustomerMenuResponse.CustomerVariantResponse> variants = new ArrayList<>();
                if (item.getVariants() != null) {
                    variants = item.getVariants().stream()
                            .filter(v -> Boolean.TRUE.equals(v.getIsActive()))
                            .map(v -> new CustomerMenuResponse.CustomerVariantResponse(
                                    v.getId(),
                                    v.getName(),
                                    v.getDescription(),
                                    v.getPrice(),
                                    Boolean.TRUE.equals(v.getIsAvailable())
                            ))
                            .toList();
                }

                // Resolve option groups (active only, with active options)
                List<CustomerMenuResponse.CustomerOptionGroupResponse> optionGroups = new ArrayList<>();
                if (item.getOptionGroups() != null) {
                    optionGroups = item.getOptionGroups().stream()
                            .filter(og -> Boolean.TRUE.equals(og.getIsActive()))
                            .map(og -> new CustomerMenuResponse.CustomerOptionGroupResponse(
                                    og.getId(),
                                    og.getName(),
                                    og.getDescription(),
                                    og.getMinSelections(),
                                    og.getMaxSelections(),
                                    Boolean.TRUE.equals(og.getIsRequired()),
                                    og.getOptions() != null ? og.getOptions().stream()
                                            .filter(o -> Boolean.TRUE.equals(o.getIsActive()))
                                            .map(o -> new CustomerMenuResponse.CustomerOptionResponse(
                                                    o.getId(),
                                                    o.getName(),
                                                    o.getDescription(),
                                                    o.getAdditionalPrice(),
                                                    Boolean.TRUE.equals(o.getIsAvailable())
                                            )).toList() : List.of()
                            ))
                            .toList();
                }

                itemResponses.add(new CustomerMenuResponse.CustomerMenuItemResponse(
                        item.getId(),
                        item.getName(),
                        item.getShortDescription(),
                        item.getDetailedDescription(),
                        effectivePrice,
                        item.getBasePrice(),
                        branchPriceOverridden,
                        item.getDietaryClassification(),
                        item.getPreparationTimeMinutes(),
                        itemAvailable,
                        primaryImg,
                        allImages,
                        variants,
                        optionGroups
                ));
            }

            categoryResponses.add(new CustomerMenuResponse.CustomerCategoryResponse(
                    cat.getId(),
                    cat.getName(),
                    cat.getDescription(),
                    cat.getDisplayOrder(),
                    cat.getIconName(),
                    itemResponses
            ));
        }

        return new CustomerMenuResponse(
                tenantId,
                restaurantName,
                tableNumber,
                currencyCode,
                taxPercentage,
                serviceChargePercentage,
                categoryResponses
        );
    }
}
