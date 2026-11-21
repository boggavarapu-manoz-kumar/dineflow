package com.dineflow.pricing;

import com.dineflow.branch.domain.Branch;
import com.dineflow.branch.repository.BranchRepository;
import com.dineflow.cart.domain.CustomerCart;
import com.dineflow.cart.domain.CustomerCartItem;
import com.dineflow.cart.repository.CustomerCartRepository;
import com.dineflow.common.exception.BusinessException;
import com.dineflow.identity.domain.CustomerSession;
import com.dineflow.identity.repository.CustomerSessionRepository;
import com.dineflow.menu.domain.DietaryClassification;
import com.dineflow.menu.domain.MenuCategory;
import com.dineflow.menu.domain.MenuItem;
import com.dineflow.menu.domain.MenuItemBranchConfiguration;
import com.dineflow.menu.domain.MenuOption;
import com.dineflow.menu.domain.MenuOptionGroup;
import com.dineflow.menu.domain.MenuVariant;
import com.dineflow.menu.repository.MenuCategoryRepository;
import com.dineflow.menu.repository.MenuItemBranchConfigurationRepository;
import com.dineflow.menu.repository.MenuItemRepository;
import com.dineflow.menu.repository.MenuOptionGroupRepository;
import com.dineflow.menu.repository.MenuOptionRepository;
import com.dineflow.menu.repository.MenuVariantRepository;
import com.dineflow.pricing.dto.PriceVerificationResult;
import com.dineflow.pricing.dto.PricingBreakdown;
import com.dineflow.pricing.service.OrderPricingEngine;
import com.dineflow.tenant.domain.HotelProfile;
import com.dineflow.tenant.repository.HotelProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class OrderPricingEngineTests {

    @Autowired
    private OrderPricingEngine pricingEngine;

    @Autowired
    private CustomerCartRepository cartRepository;

    @Autowired
    private CustomerSessionRepository sessionRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private MenuCategoryRepository categoryRepository;

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Autowired
    private MenuVariantRepository variantRepository;

    @Autowired
    private MenuOptionGroupRepository optionGroupRepository;

    @Autowired
    private MenuOptionRepository optionRepository;

    @Autowired
    private MenuItemBranchConfigurationRepository branchConfigRepository;

    @Autowired
    private HotelProfileRepository hotelProfileRepository;

    private UUID tenantId;
    private Branch branch;
    private CustomerSession session;
    private CustomerCart cart;

    private MenuItem pasta;
    private MenuItem pizza;
    private MenuVariant largePizzaVariant;
    private MenuOption extraCheese;
    private MenuOption mushrooms;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();

        // 1. HotelProfile with 5% tax and 10% service charge
        HotelProfile profile = new HotelProfile();
        profile.setTenantId(tenantId);
        profile.setCurrencyCode("INR");
        profile.setTaxPercentage(new BigDecimal("5.00"));
        profile.setServiceChargePercentage(new BigDecimal("10.00"));
        hotelProfileRepository.save(profile);

        // 2. Branch
        branch = new Branch("North Branch", "NB-01", "Park Way", "+919988776655", true, true);
        branch.setTenantId(tenantId);
        branchRepository.save(branch);

        // 3. Customer Session
        session = new CustomerSession();
        session.setTenantId(tenantId);
        session.setBranchId(branch.getId());
        session.setTableNumber("Table-07");
        session.setCustomerName("Vikram");
        session.setMaskedMobile("+91****55");
        session.setSessionTokenHash(UUID.randomUUID().toString());
        session.setStatus("ACTIVE");
        session.setExpiresAt(Instant.now().plusSeconds(3600));
        sessionRepository.save(session);

        // 4. Cart
        cart = new CustomerCart(tenantId, branch.getId(), session.getId(), "Table-07");
        cartRepository.save(cart);

        // 5. Menu Setup
        MenuCategory cat = new MenuCategory("Italian Kitchen", null, 0, true, null, null);
        cat.setTenantId(tenantId);
        categoryRepository.save(cat);

        pasta = new MenuItem(cat, "Arrabbiata Pasta", "Spicy tomato penne", null, new BigDecimal("250.00"), DietaryClassification.VEGETARIAN, 15, true, true, 0);
        pasta.setTenantId(tenantId);
        menuItemRepository.save(pasta);

        pizza = new MenuItem(cat, "Margherita Pizza", "Fresh mozzarella and basil", null, new BigDecimal("350.00"), DietaryClassification.VEGETARIAN, 20, true, true, 1);
        pizza.setTenantId(tenantId);
        menuItemRepository.save(pizza);

        largePizzaVariant = new MenuVariant(pizza, "Large 12-inch", "Extra large family size", new BigDecimal("480.00"), 0, true, true);
        largePizzaVariant.setTenantId(tenantId);
        variantRepository.save(largePizzaVariant);

        MenuOptionGroup toppings = new MenuOptionGroup("Toppings", null, 0, 5, false, 0, true);
        toppings.setTenantId(tenantId);
        optionGroupRepository.save(toppings);

        extraCheese = new MenuOption(toppings, "Fresh Mozzarella", null, new BigDecimal("45.00"), 0, true, true);
        extraCheese.setTenantId(tenantId);
        optionRepository.save(extraCheese);

        mushrooms = new MenuOption(toppings, "Wild Mushrooms", null, new BigDecimal("35.00"), 1, true, true);
        mushrooms.setTenantId(tenantId);
        optionRepository.save(mushrooms);
    }

    @Test
    @DisplayName("Item without variant: Base price multiplied by quantity with tax & service charge")
    void testItemWithoutVariant() {
        // Arrabbiata Pasta (250.00) * 2 = 500.00 subtotal
        // Tax 5% = 25.00
        // Service Charge 10% = 50.00
        // Grand Total = 575.00
        CustomerCartItem item = new CustomerCartItem(cart, pasta, null, 2, null);
        cart.addItem(item);
        cartRepository.save(cart);

        PricingBreakdown breakdown = pricingEngine.calculateCart(cart);

        assertEquals("INR", breakdown.currencyCode());
        assertEquals(2, breakdown.totalItemCount());
        assertEquals(new BigDecimal("500.00"), breakdown.itemsSubtotal());
        assertEquals(new BigDecimal("5.00"), breakdown.taxPercentage());
        assertEquals(new BigDecimal("25.00"), breakdown.taxAmount());
        assertEquals(new BigDecimal("10.00"), breakdown.serviceChargePercentage());
        assertEquals(new BigDecimal("50.00"), breakdown.serviceChargeAmount());
        assertEquals(new BigDecimal("575.00"), breakdown.grandTotal());
    }

    @Test
    @DisplayName("Item with variant: Variant price overrides base price completely")
    void testItemWithVariant() {
        // Large Margherita (480.00) * 1 = 480.00 subtotal
        // Tax 5% = 24.00
        // Service Charge 10% = 48.00
        // Grand Total = 552.00
        CustomerCartItem item = new CustomerCartItem(cart, pizza, largePizzaVariant, 1, null);
        cart.addItem(item);
        cartRepository.save(cart);

        PricingBreakdown breakdown = pricingEngine.calculateCart(cart);

        assertEquals(new BigDecimal("480.00"), breakdown.itemsSubtotal());
        assertEquals(new BigDecimal("24.00"), breakdown.taxAmount());
        assertEquals(new BigDecimal("48.00"), breakdown.serviceChargeAmount());
        assertEquals(new BigDecimal("552.00"), breakdown.grandTotal());
    }

    @Test
    @DisplayName("Item with multiple add-ons: Unit price sums variant plus all options")
    void testItemWithMultipleAddOns() {
        // Large Pizza (480.00) + Mozzarella (45.00) + Mushrooms (35.00) = 560.00 unit price
        // Quantity 2 = 1120.00 subtotal
        // Tax 5% = 56.00
        // Service Charge 10% = 112.00
        // Grand Total = 1288.00
        CustomerCartItem item = new CustomerCartItem(cart, pizza, largePizzaVariant, 2, null);
        item.getSelectedOptions().add(extraCheese);
        item.getSelectedOptions().add(mushrooms);
        cart.addItem(item);
        cartRepository.save(cart);

        PricingBreakdown breakdown = pricingEngine.calculateCart(cart);

        PricingBreakdown.CalculatedLineItem line = breakdown.lineItems().get(0);
        assertEquals(new BigDecimal("560.00"), line.effectiveUnitPrice());
        assertEquals(new BigDecimal("1120.00"), line.lineTotal());
        assertEquals(new BigDecimal("1120.00"), breakdown.itemsSubtotal());
        assertEquals(new BigDecimal("56.00"), breakdown.taxAmount());
        assertEquals(new BigDecimal("112.00"), breakdown.serviceChargeAmount());
        assertEquals(new BigDecimal("1288.00"), breakdown.grandTotal());
    }

    @Test
    @DisplayName("Branch price override takes precedence over base price when no variant is set")
    void testBranchPriceOverride() {
        // Set Branch override on pasta from 250.00 -> 290.00
        MenuItemBranchConfiguration bcfg = new MenuItemBranchConfiguration(branch, pasta, new BigDecimal("290.00"), true, true);
        bcfg.setTenantId(tenantId);
        branchConfigRepository.save(bcfg);

        CustomerCartItem item = new CustomerCartItem(cart, pasta, null, 1, null);
        cart.addItem(item);
        cartRepository.save(cart);

        PricingBreakdown breakdown = pricingEngine.calculateCart(cart);
        assertEquals(new BigDecimal("290.00"), breakdown.itemsSubtotal());
    }

    @Test
    @DisplayName("Price verification detects price changes and returns transparent warnings")
    void testPriceChangedAfterCartCreation() {
        CustomerCartItem item = new CustomerCartItem(cart, pasta, null, 2, null); // 250 * 2 = 500, with 15% tax+service = 575.00
        cart.addItem(item);
        cartRepository.save(cart);

        // Client expects total 500.00 (e.g. before price update)
        BigDecimal clientExpected = new BigDecimal("500.00");
        PriceVerificationResult result = pricingEngine.verifyPriceIntegrity(cart, clientExpected);

        assertTrue(result.hasPriceChanged());
        assertEquals(new BigDecimal("575.00"), result.authoritativeTotal());
        assertEquals(new BigDecimal("75.00"), result.difference());
        assertFalse(result.warnings().isEmpty());
    }

    @Test
    @DisplayName("Decimal rounding uses RoundingMode.HALF_UP consistently without floating point drift")
    void testDecimalRoundingPrecision() {
        // Pasta unit price 250.33, quantity 3 = 750.99
        pasta.setBasePrice(new BigDecimal("250.33"));
        menuItemRepository.save(pasta);

        CustomerCartItem item = new CustomerCartItem(cart, pasta, null, 3, null);
        cart.addItem(item);
        cartRepository.save(cart);

        PricingBreakdown breakdown = pricingEngine.calculateCart(cart);
        assertEquals(new BigDecimal("750.99"), breakdown.itemsSubtotal());

        // Tax 5% of 750.99 = 37.5495 -> 37.55
        assertEquals(new BigDecimal("37.55"), breakdown.taxAmount());
        // Service charge 10% of 750.99 = 75.099 -> 75.10
        assertEquals(new BigDecimal("75.10"), breakdown.serviceChargeAmount());
        // Grand total: 750.99 + 37.55 + 75.10 = 863.64
        assertEquals(new BigDecimal("863.64"), breakdown.grandTotal());
    }

    @Test
    @DisplayName("Item marked unavailable at branch throws BusinessException")
    void testItemUnavailableAtBranchThrows() {
        MenuItemBranchConfiguration bcfg = new MenuItemBranchConfiguration(branch, pasta, null, false, true); // Sold out at branch
        bcfg.setTenantId(tenantId);
        branchConfigRepository.save(bcfg);

        CustomerCartItem item = new CustomerCartItem(cart, pasta, null, 1, null);
        cart.addItem(item);
        cartRepository.save(cart);

        assertThrows(BusinessException.class, () -> pricingEngine.calculateCart(cart));
    }
}
