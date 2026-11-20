package com.dineflow.identity;

import com.dineflow.identity.domain.Role;
import com.dineflow.identity.domain.StaffCredentials;
import com.dineflow.identity.domain.User;
import com.dineflow.identity.domain.UserMembership;
import com.dineflow.identity.domain.UserStatus;
import com.dineflow.identity.dto.LoginRequest;
import com.dineflow.identity.repository.RoleRepository;
import com.dineflow.identity.repository.UserMembershipRepository;
import com.dineflow.identity.repository.UserRepository;
import com.dineflow.identity.service.AuthService;
import com.dineflow.security.authorization.TenantSecurityEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import com.dineflow.security.DineFlowPrincipal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdvancedAuthorizationSecurityTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserMembershipRepository membershipRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TenantSecurityEvaluator tenantSecurity;

    private UUID hotelATenantId;
    private UUID hotelBTenantId;
    private User waiterAUser;

    @BeforeEach
    void setUp() {
        hotelATenantId = UUID.randomUUID();
        hotelBTenantId = UUID.randomUUID();

        Role waiterRole = roleRepository.findByCode("WAITER")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setCode("WAITER");
                    r.setName("Waiter");
                    return roleRepository.save(r);
                });

        waiterAUser = new User();
        waiterAUser.setEmail("waiter.a@hotela.com");
        waiterAUser.setFullName("Waiter Alpha");
        waiterAUser.setStatus(UserStatus.ACTIVE);

        StaffCredentials credentials = new StaffCredentials();
        credentials.setPasswordHash(passwordEncoder.encode("Pass123!"));
        waiterAUser.setCredentials(credentials);
        waiterAUser = userRepository.save(waiterAUser);

        UserMembership membershipA = new UserMembership();
        membershipA.setUser(waiterAUser);
        membershipA.setTenantId(hotelATenantId);
        membershipA.setRole(waiterRole);
        membershipRepository.save(membershipA);
    }

    @Test
    @DisplayName("Cross-Tenant Isolation: Waiter A can access Hotel A resources but is rejected for Hotel B")
    void testCrossTenantAccessRejection() {
        DineFlowPrincipal principalA = new DineFlowPrincipal(
                waiterAUser.getId(),
                hotelATenantId,
                "WAITER",
                List.of("WAITER_VIEW", "ORDER_VIEW"),
                "STAFF"
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principalA, null, principalA.getAuthorities())
        );

        // Access to Hotel A must succeed
        assertTrue(tenantSecurity.hasTenantAccess(hotelATenantId));

        // Access to Hotel B must be denied
        assertFalse(tenantSecurity.hasTenantAccess(hotelBTenantId));

        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Super Admin possesses platform oversight across all tenants")
    void testSuperAdminGlobalOversight() {
        DineFlowPrincipal superAdminPrincipal = new DineFlowPrincipal(
                UUID.randomUUID(),
                null,
                "SUPER_ADMIN",
                List.of("PLATFORM_MANAGE", "TENANT_VIEW"),
                "STAFF"
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(superAdminPrincipal, null, superAdminPrincipal.getAuthorities())
        );

        // Super admin can access resources for both Hotel A and Hotel B
        assertTrue(tenantSecurity.hasTenantAccess(hotelATenantId));
        assertTrue(tenantSecurity.hasTenantAccess(hotelBTenantId));

        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Guest Session Isolation: Customer A cannot access Customer B session data")
    void testCustomerSessionOwnershipIsolation() {
        UUID sessionAId = UUID.randomUUID();
        UUID sessionBId = UUID.randomUUID();

        DineFlowPrincipal guestAPrincipal = new DineFlowPrincipal(
                sessionAId,
                hotelATenantId,
                "CUSTOMER_GUEST",
                List.of("CUSTOMER_ORDER_VIEW_OWN"),
                "GUEST"
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(guestAPrincipal, null, guestAPrincipal.getAuthorities())
        );

        // Session A owner can access Session A
        assertTrue(tenantSecurity.isGuestSessionOwner(sessionAId));

        // Session A owner cannot access Session B
        assertFalse(tenantSecurity.isGuestSessionOwner(sessionBId));

        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Disabled Account is blocked from authenticating")
    void testDisabledAccountBlocked() {
        waiterAUser.setStatus(UserStatus.DISABLED);
        userRepository.save(waiterAUser);

        LoginRequest request = new LoginRequest("waiter.a@hotela.com", "Pass123!", hotelATenantId);
        assertThrows(RuntimeException.class, () -> authService.login(request, "127.0.0.1", "JUnit"));
    }
}