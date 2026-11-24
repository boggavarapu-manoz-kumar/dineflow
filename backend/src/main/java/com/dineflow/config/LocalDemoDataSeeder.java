package com.dineflow.config;

import com.dineflow.branch.domain.Branch;
import com.dineflow.branch.repository.BranchRepository;
import com.dineflow.common.api.ApiResponse;
import com.dineflow.identity.domain.MembershipStatus;
import com.dineflow.identity.domain.Permission;
import com.dineflow.identity.domain.Role;
import com.dineflow.identity.domain.StaffCredentials;
import com.dineflow.identity.domain.User;
import com.dineflow.identity.domain.UserMembership;
import com.dineflow.identity.domain.UserStatus;
import com.dineflow.identity.repository.PermissionRepository;
import com.dineflow.identity.repository.RoleRepository;
import com.dineflow.identity.repository.UserRepository;
import com.dineflow.menu.domain.DietaryClassification;
import com.dineflow.menu.domain.MenuCategory;
import com.dineflow.menu.domain.MenuItem;
import com.dineflow.menu.repository.MenuCategoryRepository;
import com.dineflow.menu.repository.MenuItemRepository;
import com.dineflow.security.HmacEntryTokenVerifier;
import com.dineflow.tenant.domain.HotelProfile;
import com.dineflow.tenant.domain.Tenant;
import com.dineflow.tenant.repository.HotelProfileRepository;
import com.dineflow.tenant.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Strictly local-development demo seeder (@Profile("local")).
 * Never loaded in staging, prod, or automated test profiles.
 * Seeds a realistic demo hotel, main branch, menu catalog, RBAC roles/staff accounts,
 * and serves a live HMAC-signed QR token for zero-friction local walkthroughs.
 */
@Component
@RestController
@Profile("local")
@RequestMapping("/api/v1/health/local-demo")
public class LocalDemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalDemoDataSeeder.class);
    private static final String DEMO_SLUG = "grand-saffron-palace";
    private static final String DEMO_HOTEL_NAME = "The Grand Saffron Palace";
    private static final String DEMO_TABLE = "T-12";
    private static final String DEMO_PASSWORD = "Password@123";

    private final JdbcTemplate jdbcTemplate;
    private final TenantRepository tenantRepository;
    private final HotelProfileRepository hotelProfileRepository;
    private final BranchRepository branchRepository;
    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final MenuCategoryRepository categoryRepository;
    private final MenuItemRepository itemRepository;
    private final PasswordEncoder passwordEncoder;
    private final HmacEntryTokenVerifier entryTokenVerifier;

    private volatile UUID demoTenantId;
    private volatile UUID demoBranchId;

    public LocalDemoDataSeeder(JdbcTemplate jdbcTemplate,
                               TenantRepository tenantRepository,
                               HotelProfileRepository hotelProfileRepository,
                               BranchRepository branchRepository,
                               PermissionRepository permissionRepository,
                               RoleRepository roleRepository,
                               UserRepository userRepository,
                               MenuCategoryRepository categoryRepository,
                               MenuItemRepository itemRepository,
                               PasswordEncoder passwordEncoder,
                               HmacEntryTokenVerifier entryTokenVerifier) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantRepository = tenantRepository;
        this.hotelProfileRepository = hotelProfileRepository;
        this.branchRepository = branchRepository;
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.itemRepository = itemRepository;
        this.passwordEncoder = passwordEncoder;
        this.entryTokenVerifier = entryTokenVerifier;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureShedLockTable();

        Tenant tenant = tenantRepository.findAll().stream()
                .filter(t -> DEMO_SLUG.equalsIgnoreCase(t.getSlug()))
                .findFirst()
                .orElseGet(this::createDemoTenant);

        this.demoTenantId = tenant.getId();

        if (hotelProfileRepository.findByTenantId(demoTenantId).isEmpty()) {
            HotelProfile profile = new HotelProfile();
            profile.setTenantId(demoTenantId);
            profile.setContactEmail("concierge@grandpalace.com");
            profile.setCurrencyCode("INR");
            profile.setTaxPercentage(new BigDecimal("5.00"));
            profile.setServiceChargePercentage(new BigDecimal("5.00"));
            profile.setPrimaryColorHex("#059669");
            hotelProfileRepository.save(profile);
        }

        Branch mainBranch = branchRepository.findByTenantIdAndIsMainTrue(demoTenantId)
                .orElseGet(() -> {
                    Branch b = new Branch("Downtown Flagship", "MAIN-01",
                            "42 Royal Heritage Avenue, Bengaluru", "+91-80-4000-1234", true, true);
                    b.setTenantId(demoTenantId);
                    return branchRepository.save(b);
                });
        this.demoBranchId = mainBranch.getId();

        Map<String, Role> roles = ensureRolesAndPermissions();
        ensureDemoStaffUsers(roles);
        ensureDemoMenu();

        String qrToken = entryTokenVerifier.createSignedToken(
                demoTenantId, demoBranchId, DEMO_TABLE, Instant.now().getEpochSecond());

        log.info("==================================================================");
        log.info("DineFlow Local Demo Seeded: {} (Tenant={}, Branch={})", DEMO_HOTEL_NAME, demoTenantId, demoBranchId);
        log.info("Live Customer QR URL: http://localhost:5173/customer/entry?restaurant=The%20Grand%20Saffron%20Palace&table={}&token={}", DEMO_TABLE, qrToken);
        log.info("Demo Staff Logins (Password: {}): admin@grandpalace.com | waiter@grandpalace.com | kitchen@grandpalace.com | cashier@grandpalace.com | superadmin@dineflow.com", DEMO_PASSWORD);
        log.info("==================================================================");
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLocalDemoContext() {
        if (demoTenantId == null || demoBranchId == null) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Local demo data not initialized yet"));
        }
        String freshToken = entryTokenVerifier.createSignedToken(
                demoTenantId, demoBranchId, DEMO_TABLE, Instant.now().getEpochSecond());
        Map<String, Object> data = Map.of(
                "tenantId", demoTenantId.toString(),
                "branchId", demoBranchId.toString(),
                "restaurantName", DEMO_HOTEL_NAME,
                "tableNumber", DEMO_TABLE,
                "entryToken", freshToken
        );
        return ResponseEntity.ok(ApiResponse.ok(data, "Local demo QR context generated"));
    }

    private void ensureShedLockTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS shedlock (
                    name VARCHAR(64) NOT NULL PRIMARY KEY,
                    lock_until TIMESTAMP WITH TIME ZONE NOT NULL,
                    locked_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    locked_by VARCHAR(255) NOT NULL
                )
                """);
    }

    private Tenant createDemoTenant() {
        Tenant t = new Tenant();
        t.setSlug(DEMO_SLUG);
        t.setBusinessName(DEMO_HOTEL_NAME);
        t.setStatus("ACTIVE");
        t.setSubscriptionPlan("ENTERPRISE");
        return tenantRepository.save(t);
    }

    private Map<String, Role> ensureRolesAndPermissions() {
        List<String> allPermCodes = List.of(
                "PLATFORM_MANAGE",
                "MENU_VIEW", "MENU_CREATE", "MENU_UPDATE", "MENU_DELETE", "MENU_MANAGE",
                "ORDER_VIEW", "ORDER_ACCEPT", "ORDER_MANAGE", "ORDER_SERVE", "ORDER_CANCEL",
                "WAITER_VIEW", "WAITER_ASSIGN", "WAITER_MANAGE",
                "KITCHEN_VIEW", "KITCHEN_UPDATE", "KITCHEN_MANAGE",
                "BILL_VIEW", "BILL_CREATE", "BILL_GENERATE", "BILL_COLLECT_PAYMENT", "BILL_VOID",
                "INVOICE_VIEW", "INVOICE_GENERATE", "INVOICE_CANCEL",
                "PAYMENT_VIEW", "PAYMENT_PROCESS",
                "REFUND_REQUEST", "REFUND_APPROVE", "REFUND_EXECUTE",
                "PROMOTION_VIEW", "PROMOTION_MANAGE",
                "REPORT_FINANCIAL_VIEW"
        );

        Map<String, Permission> permMap = new HashMap<>();
        for (String code : allPermCodes) {
            Permission p = permissionRepository.findByCode(code).orElseGet(() -> {
                Permission created = new Permission();
                created.setCode(code);
                created.setDescription("Local demo permission: " + code);
                created.setCategory(code.contains("_") ? code.substring(0, code.indexOf('_')) : "GENERAL");
                return permissionRepository.save(created);
            });
            permMap.put(code, p);
        }

        Map<String, Role> roles = new HashMap<>();
        roles.put("SUPER_ADMIN", ensureRole("SUPER_ADMIN", "Super Administrator", permMap, allPermCodes));
        roles.put("HOTEL_ADMIN", ensureRole("HOTEL_ADMIN", "Hotel Administrator", permMap,
                allPermCodes.stream().filter(c -> !"PLATFORM_MANAGE".equals(c)).toList()));
        roles.put("WAITER", ensureRole("WAITER", "Waiter", permMap, List.of(
                "MENU_VIEW", "ORDER_VIEW", "ORDER_ACCEPT", "ORDER_SERVE", "WAITER_VIEW", "WAITER_ASSIGN", "WAITER_MANAGE", "BILL_VIEW", "BILL_GENERATE")));
        roles.put("KITCHEN_STAFF", ensureRole("KITCHEN_STAFF", "Kitchen Staff", permMap, List.of(
                "KITCHEN_VIEW", "KITCHEN_UPDATE", "KITCHEN_MANAGE", "ORDER_VIEW")));
        roles.put("CASHIER", ensureRole("CASHIER", "Cashier", permMap, List.of(
                "ORDER_VIEW", "BILL_VIEW", "BILL_CREATE", "BILL_GENERATE", "BILL_COLLECT_PAYMENT",
                "INVOICE_VIEW", "INVOICE_GENERATE", "PAYMENT_VIEW", "PAYMENT_PROCESS", "REFUND_REQUEST")));
        return roles;
    }

    private Role ensureRole(String code, String name, Map<String, Permission> permMap, List<String> permCodes) {
        Role role = roleRepository.findByCode(code).orElseGet(() -> {
            Role created = new Role();
            created.setCode(code);
            created.setName(name);
            created.setDescription(name + " (Local Demo)");
            created.setSystemRole(true);
            return created;
        });
        Set<Permission> perms = new HashSet<>(role.getPermissions());
        for (String pc : permCodes) {
            if (permMap.containsKey(pc)) perms.add(permMap.get(pc));
        }
        role.setPermissions(perms);
        return roleRepository.save(role);
    }

    private void ensureDemoStaffUsers(Map<String, Role> roles) {
        ensureStaffUser("admin@grandpalace.com", "Aarav Sharma (Hotel Admin)", demoTenantId, roles.get("HOTEL_ADMIN"));
        ensureStaffUser("waiter@grandpalace.com", "Rohan Verma (Waiter)", demoTenantId, roles.get("WAITER"));
        ensureStaffUser("kitchen@grandpalace.com", "Chef Vikram Rao (KDS)", demoTenantId, roles.get("KITCHEN_STAFF"));
        ensureStaffUser("cashier@grandpalace.com", "Priya Nair (Cashier)", demoTenantId, roles.get("CASHIER"));
        ensureStaffUser("superadmin@dineflow.com", "DineFlow Super Admin", null, roles.get("SUPER_ADMIN"));
    }

    private void ensureStaffUser(String email, String fullName, UUID tenantId, Role role) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            return;
        }
        User user = new User();
        user.setEmail(email);
        user.setFullName(fullName);
        user.setPhoneNumber("+919876543210");
        user.setStatus(UserStatus.ACTIVE);

        StaffCredentials creds = new StaffCredentials();
        creds.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        user.setCredentials(creds);

        UserMembership membership = new UserMembership();
        membership.setTenantId(tenantId);
        membership.setRole(role);
        membership.setStatus(MembershipStatus.ACTIVE);
        user.addMembership(membership);

        userRepository.save(user);
    }

    private void ensureDemoMenu() {
        if (!categoryRepository.findAllByTenantIdOrderByDisplayOrderAsc(demoTenantId).isEmpty()) {
            return;
        }

        MenuCategory starters = createCategory("Signature Starters", "Handcrafted tandoor & small plates", 1);
        MenuCategory mains = createCategory("Royal Main Course", "Slow-simmered curries and aromatic biryanis", 2);
        MenuCategory desserts = createCategory("Artisan Desserts & Drinks", "House-made sweets & signature coolers", 3);

        createItem(starters, "Truffle Paneer Tikka", "Char-grilled cottage cheese with saffron glaze",
                "Artisanal malai paneer marinated in Kashmiri chili, roasted cumin, and truffle oil.",
                new BigDecimal("360.00"), DietaryClassification.VEGETARIAN, 15, 1);
        createItem(starters, "Smoked Murgh Malai Kebab", "Tender charcoal-grilled chicken supreme",
                "Succulent chicken morsels infused with cardamom, cashew cream, and smoked over oak.",
                new BigDecimal("420.00"), DietaryClassification.NON_VEGETARIAN, 18, 2);
        createItem(mains, "Dal Saffron Bukhara", "24-hour slow-cooked black lentils",
                "Whole urad dal simmered overnight with vine-ripened tomatoes, white butter, and cream.",
                new BigDecimal("440.00"), DietaryClassification.VEGETARIAN, 20, 1);
        createItem(mains, "Awadhi Dum Chicken Biryani", "Aged basmati sealed in pastry crust",
                "Fragrant saffron-infused basmati rice layered with spiced chicken and caramelized onions.",
                new BigDecimal("580.00"), DietaryClassification.NON_VEGETARIAN, 25, 2);
        createItem(desserts, "Saffron Pistachio Rasmalai", "Chilled cottage cheese dumplings in saffron milk",
                "Delicate poached chenna discs steeped in reduced cardamom-saffron rabri and roasted pistachios.",
                new BigDecimal("260.00"), DietaryClassification.VEGETARIAN, 8, 1);
        createItem(desserts, "Spiced Alphonso Mango Cooler", "Fresh mango nectar with mint and lime",
                "Refreshing house-crafted cooler blending ripe Alphonso mango with roasted jeera and basil.",
                new BigDecimal("190.00"), DietaryClassification.VEGAN, 5, 2);
    }

    private MenuCategory createCategory(String name, String description, int order) {
        MenuCategory cat = new MenuCategory(name, description, order, true, "utensils", null);
        cat.setTenantId(demoTenantId);
        return categoryRepository.save(cat);
    }

    private void createItem(MenuCategory category, String name, String shortDesc, String detailedDesc,
                            BigDecimal price, DietaryClassification dietary, int prepMins, int order) {
        MenuItem item = new MenuItem(category, name, shortDesc, detailedDesc, price, dietary, prepMins, true, true, order);
        item.setTenantId(demoTenantId);
        itemRepository.save(item);
    }
}
