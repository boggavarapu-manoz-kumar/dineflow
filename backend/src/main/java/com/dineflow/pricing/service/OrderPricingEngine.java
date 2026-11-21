package com.dineflow.pricing.service;

import com.dineflow.cart.domain.CustomerCart;
import com.dineflow.cart.domain.CustomerCartItem;
import com.dineflow.common.exception.BusinessException;
import com.dineflow.menu.domain.MenuItem;
import com.dineflow.menu.domain.MenuItemBranchConfiguration;
import com.dineflow.menu.domain.MenuOption;
import com.dineflow.menu.domain.MenuVariant;
import com.dineflow.menu.repository.MenuItemBranchConfigurationRepository;
import com.dineflow.menu.repository.MenuItemRepository;
import com.dineflow.menu.repository.MenuOptionRepository;
import com.dineflow.menu.repository.MenuVariantRepository;
import com.dineflow.pricing.dto.PriceVerificationResult;
import com.dineflow.pricing.dto.PricingBreakdown;
import com.dineflow.tenant.domain.HotelProfile;
import com.dineflow.tenant.repository.HotelProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderPricingEngine {

    private final MenuItemRepository menuItemRepository;
    private final MenuVariantRepository variantRepository;
    private final MenuOptionRepository optionRepository;
    private final MenuItemBranchConfigurationRepository branchConfigRepository;
    private final HotelProfileRepository hotelProfileRepository;

    public OrderPricingEngine(MenuItemRepository menuItemRepository,
                              MenuVariantRepository variantRepository,
                              MenuOptionRepository optionRepository,
                              MenuItemBranchConfigurationRepository branchConfigRepository,
                              HotelProfileRepository hotelProfileRepository) {
        this.menuItemRepository = menuItemRepository;
        this.variantRepository = variantRepository;
        this.optionRepository = optionRepository;
        this.branchConfigRepository = branchConfigRepository;
        this.hotelProfileRepository = hotelProfileRepository;
    }

    /**
     * Calculates the authoritative PricingBreakdown for a cart or set of line items.
     */
    @Transactional(readOnly = true)
    public PricingBreakdown calculateCart(CustomerCart cart) {
        UUID tenantId = cart.getTenantId();
        UUID branchId = cart.getBranchId();

        // 1. Resolve Hotel Profile configuration
        Optional<HotelProfile> profileOpt = hotelProfileRepository.findByTenantId(tenantId);
        String currencyCode = profileOpt.map(HotelProfile::getCurrencyCode).filter(c -> !c.isBlank()).orElse("INR");
        BigDecimal taxPercentage = profileOpt.map(HotelProfile::getTaxPercentage).orElse(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal serviceChargePercentage = profileOpt.map(HotelProfile::getServiceChargePercentage).orElse(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        // 2. Load branch configurations in bulk if branch exists
        Map<UUID, MenuItemBranchConfiguration> branchConfigMap = Collections.emptyMap();
        if (branchId != null) {
            List<MenuItemBranchConfiguration> configs = branchConfigRepository.findAllByBranchIdAndTenantId(branchId, tenantId);
            branchConfigMap = configs.stream().collect(Collectors.toMap(c -> c.getMenuItem().getId(), c -> c));
        }

        // 3. Process Line Items
        List<PricingBreakdown.CalculatedLineItem> lineItems = new ArrayList<>();
        BigDecimal itemsSubtotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        int totalItemCount = 0;

        for (CustomerCartItem cartItem : cart.getItems()) {
            MenuItem mi = cartItem.getMenuItem();
            MenuVariant mv = cartItem.getVariant();

            // Refresh latest item from repository to guard against stale cached entities
            MenuItem currentMenuItem = menuItemRepository.findByIdAndTenantId(mi.getId(), tenantId)
                    .orElseThrow(() -> new BusinessException("Menu item '" + mi.getName() + "' is no longer available", HttpStatus.BAD_REQUEST, "ITEM_NOT_FOUND"));

            if (!Boolean.TRUE.equals(currentMenuItem.getIsActive())) {
                throw new BusinessException("Menu item '" + currentMenuItem.getName() + "' is no longer active", HttpStatus.BAD_REQUEST, "ITEM_INACTIVE");
            }

            // Check branch availability & overrides
            BigDecimal basePrice = currentMenuItem.getBasePrice().setScale(2, RoundingMode.HALF_UP);
            if (branchId != null) {
                MenuItemBranchConfiguration bcfg = branchConfigMap.get(currentMenuItem.getId());
                if (bcfg != null) {
                    if (!Boolean.TRUE.equals(bcfg.getIsVisible()) || !Boolean.TRUE.equals(bcfg.getIsAvailable())) {
                        throw new BusinessException("Item '" + currentMenuItem.getName() + "' is sold out at this branch", HttpStatus.BAD_REQUEST, "ITEM_UNAVAILABLE_AT_BRANCH");
                    }
                    if (bcfg.getPriceOverride() != null) {
                        basePrice = bcfg.getPriceOverride().setScale(2, RoundingMode.HALF_UP);
                    }
                }
            }

            if (!Boolean.TRUE.equals(currentMenuItem.getIsAvailable())) {
                throw new BusinessException("Item '" + currentMenuItem.getName() + "' is sold out", HttpStatus.BAD_REQUEST, "ITEM_SOLD_OUT");
            }

            // Variant pricing
            BigDecimal variantPrice = null;
            if (mv != null) {
                MenuVariant currentVariant = variantRepository.findByIdAndTenantId(mv.getId(), tenantId)
                        .orElseThrow(() -> new BusinessException("Variant not found", HttpStatus.BAD_REQUEST, "VARIANT_NOT_FOUND"));

                if (!Boolean.TRUE.equals(currentVariant.getIsActive()) || !Boolean.TRUE.equals(currentVariant.getIsAvailable())) {
                    throw new BusinessException("Variant '" + currentVariant.getName() + "' is unavailable", HttpStatus.BAD_REQUEST, "VARIANT_UNAVAILABLE");
                }

                variantPrice = currentVariant.getPrice().setScale(2, RoundingMode.HALF_UP);
            }

            // Unit base price: Variant price takes absolute precedence over base item price when selected
            BigDecimal effectiveItemBase = variantPrice != null ? variantPrice : basePrice;

            // Option pricing
            BigDecimal optionsSubtotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            List<PricingBreakdown.CalculatedOption> calculatedOptions = new ArrayList<>();
            if (cartItem.getSelectedOptions() != null) {
                for (MenuOption opt : cartItem.getSelectedOptions()) {
                    MenuOption currentOption = optionRepository.findById(opt.getId())
                            .filter(o -> o.getTenantId().equals(tenantId))
                            .orElseThrow(() -> new BusinessException("Add-on option '" + opt.getName() + "' was not found", HttpStatus.BAD_REQUEST, "OPTION_NOT_FOUND"));

                    if (!Boolean.TRUE.equals(currentOption.getIsActive()) || !Boolean.TRUE.equals(currentOption.getIsAvailable())) {
                        throw new BusinessException("Option '" + currentOption.getName() + "' is currently unavailable", HttpStatus.BAD_REQUEST, "OPTION_UNAVAILABLE");
                    }

                    BigDecimal optPrice = currentOption.getAdditionalPrice().setScale(2, RoundingMode.HALF_UP);
                    optionsSubtotal = optionsSubtotal.add(optPrice);
                    calculatedOptions.add(new PricingBreakdown.CalculatedOption(currentOption.getId(), currentOption.getName(), optPrice));
                }
            }

            BigDecimal effectiveUnitPrice = effectiveItemBase.add(optionsSubtotal).setScale(2, RoundingMode.HALF_UP);
            int qty = cartItem.getQuantity();
            if (qty <= 0 || qty > 50) {
                throw new BusinessException("Quantity must be between 1 and 50", HttpStatus.BAD_REQUEST, "INVALID_QUANTITY");
            }

            BigDecimal lineTotal = effectiveUnitPrice.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            itemsSubtotal = itemsSubtotal.add(lineTotal);
            totalItemCount += qty;

            lineItems.add(new PricingBreakdown.CalculatedLineItem(
                    cartItem.getId(),
                    currentMenuItem.getId(),
                    currentMenuItem.getName(),
                    mv != null ? mv.getId() : null,
                    mv != null ? mv.getName() : null,
                    basePrice,
                    variantPrice,
                    optionsSubtotal,
                    effectiveUnitPrice,
                    qty,
                    lineTotal,
                    calculatedOptions
            ));
        }

        // 4. Financial Taxes and Service Charges
        BigDecimal taxAmount = itemsSubtotal.multiply(taxPercentage)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal serviceChargeAmount = itemsSubtotal.multiply(serviceChargePercentage)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal grandTotal = itemsSubtotal.add(taxAmount).add(serviceChargeAmount).setScale(2, RoundingMode.HALF_UP);

        return new PricingBreakdown(
                currencyCode,
                totalItemCount,
                itemsSubtotal,
                taxPercentage,
                taxAmount,
                serviceChargePercentage,
                serviceChargeAmount,
                grandTotal,
                lineItems
        );
    }

    /**
     * Checks if the client's expected total matches the server's authoritative calculation.
     * Prevents charging stale prices if prices were updated after the cart was opened.
     */
    @Transactional(readOnly = true)
    public PriceVerificationResult verifyPriceIntegrity(CustomerCart cart, BigDecimal clientExpectedTotal) {
        PricingBreakdown authoritative = calculateCart(cart);

        List<String> warnings = new ArrayList<>();
        boolean priceChanged = false;
        BigDecimal diff = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        if (clientExpectedTotal != null) {
            BigDecimal expectedScaled = clientExpectedTotal.setScale(2, RoundingMode.HALF_UP);
            if (!expectedScaled.equals(authoritative.grandTotal())) {
                priceChanged = true;
                diff = authoritative.grandTotal().subtract(expectedScaled).setScale(2, RoundingMode.HALF_UP);
                warnings.add("The order total changed from " + authoritative.currencyCode() + " " + expectedScaled +
                        " to " + authoritative.currencyCode() + " " + authoritative.grandTotal() + " due to recent menu price adjustments.");
            }
        }

        return new PriceVerificationResult(
                priceChanged,
                false,
                clientExpectedTotal != null ? clientExpectedTotal.setScale(2, RoundingMode.HALF_UP) : null,
                authoritative.grandTotal(),
                diff,
                warnings,
                authoritative
        );
    }
}
