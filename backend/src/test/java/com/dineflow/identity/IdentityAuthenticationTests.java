package com.dineflow.identity;

import com.dineflow.identity.domain.Permission;
import com.dineflow.identity.domain.Role;
import com.dineflow.identity.domain.StaffCredentials;
import com.dineflow.identity.domain.User;
import com.dineflow.identity.domain.UserMembership;
import com.dineflow.identity.domain.UserStatus;
import com.dineflow.identity.dto.CustomerSessionRequest;
import com.dineflow.identity.dto.CustomerSessionResponse;
import com.dineflow.identity.dto.LoginRequest;
import com.dineflow.identity.repository.CustomerSessionRepository;
import com.dineflow.identity.repository.PermissionRepository;
import com.dineflow.identity.repository.RefreshSessionRepository;
import com.dineflow.identity.repository.RoleRepository;
import com.dineflow.identity.repository.UserMembershipRepository;
import com.dineflow.identity.repository.UserRepository;
import com.dineflow.identity.service.AuthService;
import com.dineflow.security.HmacEntryTokenVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IdentityAuthenticationTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserMembershipRepository membershipRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private HmacEntryTokenVerifier hmacEntryTokenVerifier;

    private User testUser;
    private UUID testTenantId;

    @BeforeEach
    void setUp() {
        testTenantId = UUID.randomUUID();

        Permission orderView = permissionRepository.findByCode("ORDER_VIEW")
                .orElseGet(() -> {
                    Permission p = new Permission();
                    p.setCode("ORDER_VIEW");
                    p.setDescription("View orders");
                    p.setCategory("ORDER");
                    return permissionRepository.save(p);
                });

        Role adminRole = roleRepository.findByCode("HOTEL_ADMIN")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setCode("HOTEL_ADMIN");
                    r.setName("Hotel Administrator");
                    r.setPermissions(Set.of(orderView));
                    return roleRepository.save(r);
                });

        testUser = new User();
        testUser.setEmail("staff@dineflow-test.com");
        testUser.setFullName("John Doe");
        testUser.setStatus(UserStatus.ACTIVE);

        StaffCredentials credentials = new StaffCredentials();
        credentials.setPasswordHash(passwordEncoder.encode("SecurePass123!"));
        testUser.setCredentials(credentials);

        testUser = userRepository.save(testUser);

        UserMembership membership = new UserMembership();
        membership.setUser(testUser);
        membership.setTenantId(testTenantId);
        membership.setRole(adminRole);
        membershipRepository.save(membership);
    }

    @Test
    @DisplayName("Staff login succeeds with valid credentials and issues access & refresh tokens")
    void testStaffLoginSuccess() {
        LoginRequest request = new LoginRequest("staff@dineflow-test.com", "SecurePass123!", testTenantId);
        AuthService.AuthResult result = authService.login(request, "127.0.0.1", "JUnit-Agent");

        assertNotNull(result.loginResponse().accessToken());
        assertNotNull(result.rawRefreshToken());
        assertEquals("staff@dineflow-test.com", result.loginResponse().user().email());
        assertEquals("HOTEL_ADMIN", result.loginResponse().user().role());
    }

    @Test
    @DisplayName("Staff login fails with bad password")
    void testStaffLoginBadPassword() {
        LoginRequest request = new LoginRequest("staff@dineflow-test.com", "WrongPassword", testTenantId);
        assertThrows(RuntimeException.class, () -> authService.login(request, "127.0.0.1", "JUnit-Agent"));
    }

    @Test
    @DisplayName("Rotating refresh token succeeds and invalidates previous token")
    void testRefreshTokenRotation() {
        LoginRequest request = new LoginRequest("staff@dineflow-test.com", "SecurePass123!", testTenantId);
        AuthService.AuthResult loginResult = authService.login(request, "127.0.0.1", "JUnit-Agent");

        String originalRefreshToken = loginResult.rawRefreshToken();

        AuthService.AuthResult refreshResult = authService.refreshSession(originalRefreshToken, "127.0.0.1", "JUnit-Agent");
        assertNotNull(refreshResult.loginResponse().accessToken());
        assertNotNull(refreshResult.rawRefreshToken());

        // Token Reuse Detection
        assertThrows(RuntimeException.class, () ->
                authService.refreshSession(originalRefreshToken, "127.0.0.1", "JUnit-Agent"));
    }

    @Test
    @DisplayName("Customer guest session created with valid signed QR entry token")
    void testCustomerGuestSessionCreation() {
        String signedToken = hmacEntryTokenVerifier.createSignedToken(testTenantId, "Table-12", java.time.Instant.now().getEpochSecond());

        CustomerSessionRequest request = new CustomerSessionRequest("Rahul", "+919876543210", signedToken);
        CustomerSessionResponse response = authService.createCustomerSession(request);

        assertNotNull(response.guestToken());
        assertEquals(testTenantId, response.tenantId());
        assertEquals("Table-12", response.tableNumber());
        assertEquals("Rahul", response.customerName());
    }

    @Test
    @DisplayName("Customer guest session fails with forged or invalid signed entry token")
    void testCustomerGuestSessionForgedToken() {
        CustomerSessionRequest request = new CustomerSessionRequest("Hacker", "+919999999999", "forged-invalid-entry-token");
        assertThrows(RuntimeException.class, () -> authService.createCustomerSession(request));
    }
}