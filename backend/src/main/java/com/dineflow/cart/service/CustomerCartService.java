package com.dineflow.cart.service;

import com.dineflow.branch.domain.Branch;
import com.dineflow.branch.repository.BranchRepository;
import com.dineflow.cart.domain.CustomerCart;
import com.dineflow.cart.domain.CustomerCartItem;
import com.dineflow.cart.dto.AddCartItemRequest;
import com.dineflow.cart.dto.CartResponse;
import com.dineflow.cart.dto.UpdateCartItemQuantityRequest;
import com.dineflow.cart.repository.CustomerCartRepository;
import com.dineflow.common.exception.BusinessException;
import com.dineflow.common.exception.ResourceNotFoundException;
import com.dineflow.identity.domain.CustomerSession;
import com.dineflow.identity.repository.CustomerSessionRepository;
import com.dineflow.menu.domain.MenuItem;
import com.dineflow.menu.domain.MenuItemBranchConfiguration;
import com.dineflow.menu.domain.MenuOption;
import com.dineflow.menu.domain.MenuOptionGroup;
import com.dineflow.menu.domain.MenuVariant;
import com.dineflow.menu.repository.MenuItemBranchConfigurationRepository;
import com.dineflow.menu.repository.MenuItemRepository;
import com.dineflow.menu.repository.MenuOptionRepository;
import com.dineflow.menu.repository.MenuVariantRepository;
import com.dineflow.security.DineFlowPrincipal;
import com.dineflow.tenant.domain.HotelProfile;
import com.dineflow.tenant.repository.HotelProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CustomerCartService {

    private final CustomerCartRepository cartRepository;
    private final CustomerSessionRepository sessionRepository;
    private final MenuItemRepository menuItemRepository;
    private final MenuVariantRepository variantRepository;
    private final MenuOptionRepository optionRepository;
    private final MenuItemBranchConfigurationRepository branchConfigRepository;
    private final HotelProfileRepository hotelProfileRepository;
    private final BranchRepository branchRepository;
    private final com.dineflow.pricing.service.OrderPricingEngine pricingEngine;

    public CustomerCartService(CustomerCartRepository cartRepository,
                               CustomerSessionRepository sessionRepository,
                               MenuItemRepository menuItemRepository,
                               MenuVariantRepository variantRepository,
                               MenuOptionRepository optionRepository,
                               MenuItemBranchConfigurationRepository branchConfigRepository,
                               HotelProfileRepository hotelProfileRepository,
                               BranchRepository branchRepository,
                               com.dineflow.pricing.service.OrderPricingEngine pricingEngine) {
        this.cartRepository = cartRepository;
        this.sessionRepository = sessionRepository;
        this.menuItemRepository = menuItemRepository;
        this.variantRepository = variantRepository;
        this.optionRepository = optionRepository;
        this.branchConfigRepository = branchConfigRepository;
        this.hotelProfileRepository = hotelProfileRepository;
        this.branchRepository = branchRepository;
        this.pricingEngine = pricingEngine;
    }

    @Transactional
    public CartResponse getOrCreateCart(DineFlowPrincipal principal) {
        CustomerCart cart = resolveOrCreateCart(principal);
        return calculateAndBuildResponse(cart);
    }

    @Transactional
    public CartResponse addItemToCart(DineFlowPrincipal principal, AddCartItemRequest request) {
        CustomerCart cart = resolveOrCreateCart(principal);
        UUID tenantId = cart.getTenantId();
        UUID branchId = cart.getBranchId();

        // 1. Validate MenuItem
        MenuItem menuItem = menuItemRepository.findByIdAndTenantId(request.menuItemId(), tenantId)
                .orElseThrow(() -> new BusinessException("Menu item not found for this restaurant", HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND"));

        if (!Boolean.TRUE.equals(menuItem.getIsActive())) {
            throw new BusinessException("Selected menu item is currently inactive", HttpStatus.BAD_REQUEST, "ITEM_INACTIVE");
        }

        // Branch availability & visibility check
        if (branchId != null) {
            Optional<MenuItemBranchConfiguration> branchConfigOpt = branchConfigRepository
                    .findByBranchIdAndMenuItemIdAndTenantId(branchId, menuItem.getId(), tenantId);
            if (branchConfigOpt.isPresent()) {
                MenuItemBranchConfiguration cfg = branchConfigOpt.get();
                if (!Boolean.TRUE.equals(cfg.getIsVisible()) || !Boolean.TRUE.equals(cfg.getIsAvailable())) {
                    throw new BusinessException("Selected item is not available at this branch", HttpStatus.BAD_REQUEST, "ITEM_UNAVAILABLE_AT_BRANCH");
                }
            }
        }

        if (!Boolean.TRUE.equals(menuItem.getIsAvailable())) {
            throw new BusinessException("Selected item is currently sold out", HttpStatus.BAD_REQUEST, "ITEM_SOLD_OUT");
        }

        // 2. Validate Variant (if supplied or required)
        MenuVariant variant = null;
        if (request.variantId() != null) {
            variant = variantRepository.findByIdAndTenantId(request.variantId(), tenantId)
                    .orElseThrow(() -> new BusinessException("Variant not found", HttpStatus.NOT_FOUND, "VARIANT_NOT_FOUND"));

            if (!variant.getMenuItem().getId().equals(menuItem.getId())) {
                throw new BusinessException("Variant does not belong to the selected item", HttpStatus.BAD_REQUEST, "INVALID_VARIANT_ASSIGNMENT");
            }
            if (!Boolean.TRUE.equals(variant.getIsActive()) || !Boolean.TRUE.equals(variant.getIsAvailable())) {
                throw new BusinessException("Selected variant is currently unavailable", HttpStatus.BAD_REQUEST, "VARIANT_UNAVAILABLE");
            }
        } else if (menuItem.getVariants() != null && !menuItem.getVariants().isEmpty()) {
            boolean hasActiveVariants = menuItem.getVariants().stream().anyMatch(v -> Boolean.TRUE.equals(v.getIsActive()));
            if (hasActiveVariants) {
                throw new BusinessException("Please choose a portion size or variant for this item", HttpStatus.BAD_REQUEST, "VARIANT_REQUIRED");
            }
        }

        // 3. Validate Options
        Set<MenuOption> selectedOptions = new HashSet<>();
        if (request.optionIds() != null && !request.optionIds().isEmpty()) {
            List<MenuOption> options = optionRepository.findAllById(request.optionIds());
            if (options.size() != request.optionIds().size()) {
                throw new BusinessException("One or more selected add-ons were not found", HttpStatus.BAD_REQUEST, "OPTION_NOT_FOUND");
            }

            // Verify all options belong to this tenant, are active, and belong to an option group linked to this item
            Set<UUID> allowedOptionGroupIds = menuItem.getOptionGroups().stream()
                    .filter(g -> Boolean.TRUE.equals(g.getIsActive()))
                    .map(MenuOptionGroup::getId)
                    .collect(Collectors.toSet());

            for (MenuOption opt : options) {
                if (!opt.getTenantId().equals(tenantId)) {
                    throw new BusinessException("Cross-tenant option detected", HttpStatus.BAD_REQUEST, "CROSS_TENANT_OPTION");
                }
                if (!Boolean.TRUE.equals(opt.getIsActive()) || !Boolean.TRUE.equals(opt.getIsAvailable())) {
                    throw new BusinessException("Option '" + opt.getName() + "' is unavailable", HttpStatus.BAD_REQUEST, "OPTION_UNAVAILABLE");
                }
                if (!allowedOptionGroupIds.contains(opt.getOptionGroup().getId())) {
                    throw new BusinessException("Option '" + opt.getName() + "' is not valid for this menu item", HttpStatus.BAD_REQUEST, "INVALID_OPTION_GROUP");
                }
            }
            selectedOptions.addAll(options);
        }

        // 4. Merge duplicate line or create new line
        final MenuVariant finalVariant = variant;
        final Set<UUID> selectedOptionIds = selectedOptions.stream().map(MenuOption::getId).collect(Collectors.toSet());

        Optional<CustomerCartItem> existingItemOpt = cart.getItems().stream()
                .filter(item -> item.getMenuItem().getId().equals(menuItem.getId()))
                .filter(item -> (finalVariant == null && item.getVariant() == null) ||
                                (finalVariant != null && item.getVariant() != null && finalVariant.getId().equals(item.getVariant().getId())))
                .filter(item -> {
                    Set<UUID> itemOptIds = item.getSelectedOptions().stream().map(MenuOption::getId).collect(Collectors.toSet());
                    return itemOptIds.equals(selectedOptionIds);
                })
                .findFirst();

        if (existingItemOpt.isPresent()) {
            CustomerCartItem existing = existingItemOpt.get();
            int newQuantity = existing.getQuantity() + request.quantity();
            if (newQuantity > 50) {
                throw new BusinessException("Maximum quantity limit (50) reached for this item", HttpStatus.BAD_REQUEST, "QUANTITY_LIMIT_EXCEEDED");
            }
            existing.setQuantity(newQuantity);
            if (request.specialInstructions() != null && !request.specialInstructions().isBlank()) {
                existing.setSpecialInstructions(request.specialInstructions().trim());
            }
        } else {
            CustomerCartItem newItem = new CustomerCartItem(cart, menuItem, variant, request.quantity(),
                    request.specialInstructions() != null ? request.specialInstructions().trim() : null);
            newItem.setSelectedOptions(selectedOptions);
            cart.addItem(newItem);
        }

        cartRepository.save(cart);
        return calculateAndBuildResponse(cart);
    }

    @Transactional
    public CartResponse updateItemQuantity(DineFlowPrincipal principal, UUID cartItemId, UpdateCartItemQuantityRequest request) {
        CustomerCart cart = resolveOrCreateCart(principal);

        CustomerCartItem item = cart.getItems().stream()
                .filter(i -> i.getId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Cart item not found", HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND"));

        item.setQuantity(request.quantity());
        cartRepository.save(cart);
        return calculateAndBuildResponse(cart);
    }

    @Transactional
    public CartResponse removeItemFromCart(DineFlowPrincipal principal, UUID cartItemId) {
        CustomerCart cart = resolveOrCreateCart(principal);

        CustomerCartItem item = cart.getItems().stream()
                .filter(i -> i.getId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Cart item not found", HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND"));

        cart.removeItem(item);
        cartRepository.save(cart);
        return calculateAndBuildResponse(cart);
    }

    @Transactional
    public CartResponse clearCart(DineFlowPrincipal principal) {
        CustomerCart cart = resolveOrCreateCart(principal);
        cart.clearItems();
        cartRepository.save(cart);
        return calculateAndBuildResponse(cart);
    }

    @Transactional(readOnly = true)
    public com.dineflow.pricing.dto.PriceVerificationResult verifyCartPrice(DineFlowPrincipal principal, BigDecimal expectedTotal) {
        CustomerCart cart = resolveOrCreateCart(principal);
        return pricingEngine.verifyPriceIntegrity(cart, expectedTotal);
    }

    private CustomerCart resolveOrCreateCart(DineFlowPrincipal principal) {
        UUID tenantId = principal.getTenantId();
        UUID sessionId = principal.getUserId();

        CustomerSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException("Customer guest session expired or invalid", HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED"));

        if (!"ACTIVE".equals(session.getStatus())) {
            throw new BusinessException("Customer session is no longer active", HttpStatus.FORBIDDEN, "SESSION_INACTIVE");
        }

        UUID branchId = session.getBranchId() != null ? session.getBranchId() : principal.getBranchId();

        return cartRepository.findByCustomerSessionIdAndTenantId(sessionId, tenantId)
                .orElseGet(() -> {
                    CustomerCart newCart = new CustomerCart(tenantId, branchId, sessionId, session.getTableNumber());
                    return cartRepository.save(newCart);
                });
    }

    /**
     * Authoritative Server-Side Pricing Engine
     */
    private CartResponse calculateAndBuildResponse(CustomerCart cart) {
        UUID tenantId = cart.getTenantId();
        UUID branchId = cart.getBranchId();

        // 1. Resolve Hotel Profile for currency & taxes
        Optional<HotelProfile> profileOpt = hotelProfileRepository.findByTenantId(tenantId);
        String currencyCode = "INR";
        BigDecimal taxPercentage = BigDecimal.ZERO;
        BigDecimal serviceChargePercentage = BigDecimal.ZERO;

        if (profileOpt.isPresent()) {
            HotelProfile profile = profileOpt.get();
            if (profile.getCurrencyCode() != null) currencyCode = profile.getCurrencyCode();
            if (profile.getTaxPercentage() != null) taxPercentage = profile.getTaxPercentage();
            if (profile.getServiceChargePercentage() != null) serviceChargePercentage = profile.getServiceChargePercentage();
        }

        // 2. Load Branch overrides in bulk if branch context exists
        Map<UUID, MenuItemBranchConfiguration> branchConfigMap = Map.of();
        if (branchId != null) {
            List<MenuItemBranchConfiguration> configs = branchConfigRepository.findAllByBranchIdAndTenantId(branchId, tenantId);
            branchConfigMap = configs.stream().collect(Collectors.toMap(c -> c.getMenuItem().getId(), c -> c));
        }

        // 3. Compute item calculations
        List<CartResponse.CartItemResponse> itemResponses = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        int totalItemCount = 0;

        for (CustomerCartItem cartItem : cart.getItems()) {
            MenuItem mi = cartItem.getMenuItem();
            MenuVariant mv = cartItem.getVariant();

            // Determine base unit price
            BigDecimal unitPrice;
            if (mv != null) {
                // If variant chosen, variant full price is authoritative base
                unitPrice = mv.getPrice();
            } else {
                unitPrice = mi.getBasePrice();
                // Check branch override
                MenuItemBranchConfiguration bcfg = branchConfigMap.get(mi.getId());
                if (bcfg != null && bcfg.getPriceOverride() != null) {
                    unitPrice = bcfg.getPriceOverride();
                }
            }

            // Sum additional option prices
            BigDecimal optionsSum = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            List<CartResponse.CartOptionResponse> optResponses = new ArrayList<>();
            if (cartItem.getSelectedOptions() != null) {
                for (MenuOption opt : cartItem.getSelectedOptions()) {
                    optionsSum = optionsSum.add(opt.getAdditionalPrice());
                    optResponses.add(new CartResponse.CartOptionResponse(opt.getId(), opt.getName(), opt.getAdditionalPrice()));
                }
            }

            BigDecimal effectiveItemUnitPrice = unitPrice.add(optionsSum).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = effectiveItemUnitPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity())).setScale(2, RoundingMode.HALF_UP);

            subtotal = subtotal.add(lineTotal);
            totalItemCount += cartItem.getQuantity();

            // Verify availability at branch
            boolean isAvailable = Boolean.TRUE.equals(mi.getIsActive()) && Boolean.TRUE.equals(mi.getIsAvailable());
            if (branchId != null) {
                MenuItemBranchConfiguration bcfg = branchConfigMap.get(mi.getId());
                if (bcfg != null && (!Boolean.TRUE.equals(bcfg.getIsVisible()) || !Boolean.TRUE.equals(bcfg.getIsAvailable()))) {
                    isAvailable = false;
                }
            }

            itemResponses.add(new CartResponse.CartItemResponse(
                    cartItem.getId(),
                    mi.getId(),
                    mi.getName(),
                    mv != null ? mv.getId() : null,
                    mv != null ? mv.getName() : null,
                    effectiveItemUnitPrice,
                    cartItem.getQuantity(),
                    lineTotal,
                    cartItem.getSpecialInstructions(),
                    isAvailable,
                    optResponses
            ));
        }

        // 4. Compute Tax and Service Charge
        BigDecimal taxAmount = subtotal.multiply(taxPercentage)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal serviceChargeAmount = subtotal.multiply(serviceChargePercentage)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal grandTotal = subtotal.add(taxAmount).add(serviceChargeAmount).setScale(2, RoundingMode.HALF_UP);

        return new CartResponse(
                cart.getId(),
                cart.getTenantId(),
                cart.getBranchId(),
                cart.getCustomerSessionId(),
                cart.getTableNumber(),
                currencyCode,
                totalItemCount,
                subtotal,
                taxPercentage,
                taxAmount,
                serviceChargePercentage,
                serviceChargeAmount,
                grandTotal,
                itemResponses
        );
    }
}
