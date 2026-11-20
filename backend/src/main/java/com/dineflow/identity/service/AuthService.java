package com.dineflow.identity.service;

import com.dineflow.common.exception.BusinessException;
import com.dineflow.identity.domain.CustomerSession;
import com.dineflow.identity.domain.Permission;
import com.dineflow.identity.domain.RefreshSession;
import com.dineflow.identity.domain.StaffCredentials;
import com.dineflow.identity.domain.User;
import com.dineflow.identity.domain.UserMembership;
import com.dineflow.identity.domain.UserStatus;
import com.dineflow.identity.dto.CustomerSessionRequest;
import com.dineflow.identity.dto.CustomerSessionResponse;
import com.dineflow.identity.dto.LoginRequest;
import com.dineflow.identity.dto.LoginResponse;
import com.dineflow.identity.repository.CustomerSessionRepository;
import com.dineflow.identity.repository.RefreshSessionRepository;
import com.dineflow.identity.repository.UserMembershipRepository;
import com.dineflow.identity.repository.UserRepository;
import com.dineflow.security.EntryTokenVerifier;
import com.dineflow.security.JwtProperties;
import com.dineflow.security.JwtTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final UserMembershipRepository membershipRepository;
    private final RefreshSessionRepository refreshSessionRepository;
    private final CustomerSessionRepository customerSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;
    private final EntryTokenVerifier entryTokenVerifier;
    private final AuditLogService auditLogService;

    public AuthService(UserRepository userRepository,
                       UserMembershipRepository membershipRepository,
                       RefreshSessionRepository refreshSessionRepository,
                       CustomerSessionRepository customerSessionRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService,
                       JwtProperties jwtProperties,
                       EntryTokenVerifier entryTokenVerifier,
                       AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.refreshSessionRepository = refreshSessionRepository;
        this.customerSessionRepository = customerSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.jwtProperties = jwtProperties;
        this.entryTokenVerifier = entryTokenVerifier;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public AuthResult login(LoginRequest request, String ipAddress, String userAgent) {
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(request.identifier());
        if (userOpt.isEmpty()) {
            auditLogService.recordAuthEvent(request.identifier(), request.tenantId(), "FAILED_CREDENTIALS", "Unknown user", ipAddress, userAgent);
            throw new BusinessException("Invalid email or password", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }

        User user = userOpt.get();

        if (user.getStatus() != UserStatus.ACTIVE) {
            auditLogService.recordAuthEvent(user.getEmail(), request.tenantId(), "ACCOUNT_BLOCKED", "User status: " + user.getStatus(), ipAddress, userAgent);
            throw new BusinessException("Account is " + user.getStatus(), HttpStatus.FORBIDDEN, "ACCOUNT_" + user.getStatus());
        }

        StaffCredentials credentials = user.getCredentials();
        if (credentials == null || credentials.getPasswordHash() == null) {
            auditLogService.recordAuthEvent(user.getEmail(), request.tenantId(), "FAILED_CREDENTIALS", "No staff credentials", ipAddress, userAgent);
            throw new BusinessException("Invalid email or password", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }

        if (credentials.getLockedUntil() != null && credentials.getLockedUntil().isAfter(Instant.now())) {
            auditLogService.recordAuthEvent(user.getEmail(), request.tenantId(), "ACCOUNT_LOCKED", "Locked until " + credentials.getLockedUntil(), ipAddress, userAgent);
            throw new BusinessException("Account is temporarily locked due to multiple failed login attempts",
                    HttpStatus.FORBIDDEN, "ACCOUNT_LOCKED");
        }

        if (!passwordEncoder.matches(request.password(), credentials.getPasswordHash())) {
            int attempts = credentials.getFailedLoginAttempts() + 1;
            credentials.setFailedLoginAttempts(attempts);
            if (attempts >= 5) {
                credentials.setLockedUntil(Instant.now().plusSeconds(900));
                log.warn("Account locked for user: {}", user.getEmail());
                auditLogService.recordAuthEvent(user.getEmail(), request.tenantId(), "ACCOUNT_LOCKED", "Exceeded max failed attempts", ipAddress, userAgent);
            } else {
                auditLogService.recordAuthEvent(user.getEmail(), request.tenantId(), "FAILED_CREDENTIALS", "Bad password attempt " + attempts, ipAddress, userAgent);
            }
            throw new BusinessException("Invalid email or password", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }

        // Reset failed counters
        credentials.setFailedLoginAttempts(0);
        credentials.setLockedUntil(null);

        // Resolve Membership
        UserMembership membership;
        if (request.tenantId() != null) {
            membership = membershipRepository.findByUserIdAndTenantId(user.getId(), request.tenantId())
                    .orElseThrow(() -> {
                        auditLogService.recordAuthEvent(user.getEmail(), request.tenantId(), "TENANT_ACCESS_DENIED", "No membership in requested tenant", ipAddress, userAgent);
                        return new BusinessException("No membership found for requested tenant", HttpStatus.FORBIDDEN, "TENANT_ACCESS_DENIED");
                    });
        } else {
            List<UserMembership> memberships = membershipRepository.findByUserId(user.getId());
            if (memberships.isEmpty()) {
                auditLogService.recordAuthEvent(user.getEmail(), null, "NO_MEMBERSHIP", "User has no memberships", ipAddress, userAgent);
                throw new BusinessException("No active memberships for user", HttpStatus.FORBIDDEN, "NO_MEMBERSHIP");
            }
            membership = memberships.get(0);
        }

        List<String> permissions = membership.getRole().getPermissions().stream()
                .map(Permission::getCode)
                .toList();

        String accessToken = jwtTokenService.generateAccessToken(
                user.getId(),
                membership.getTenantId(),
                membership.getRole().getCode(),
                permissions,
                "STAFF"
        );

        String rawRefreshToken = generateRandomToken();
        String tokenHash = hashToken(rawRefreshToken);
        UUID familyId = UUID.randomUUID();

        RefreshSession session = new RefreshSession();
        session.setUser(user);
        session.setTenantId(membership.getTenantId());
        session.setTokenHash(tokenHash);
        session.setFamilyId(familyId);
        session.setRevoked(false);
        session.setExpiresAt(Instant.now().plusSeconds(jwtProperties.getRefreshTokenExpirationSeconds()));
        session.setIpAddress(ipAddress);
        session.setUserAgent(userAgent);
        refreshSessionRepository.save(session);

        auditLogService.recordAuthEvent(user.getEmail(), membership.getTenantId(), "SUCCESS", "Staff authenticated", ipAddress, userAgent);

        LoginResponse.UserProfileResponse userProfile = new LoginResponse.UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus().name(),
                membership.getTenantId(),
                membership.getRole().getCode(),
                permissions
        );

        LoginResponse loginResponse = new LoginResponse(accessToken, jwtProperties.getAccessTokenExpirationSeconds(), userProfile);
        return new AuthResult(loginResponse, rawRefreshToken);
    }

    @Transactional
    public AuthResult refreshSession(String rawRefreshToken, String ipAddress, String userAgent) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BusinessException("Missing refresh token", HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN");
        }

        String presentedHash = hashToken(rawRefreshToken);
        RefreshSession session = refreshSessionRepository.findByTokenHash(presentedHash)
                .orElseThrow(() -> new BusinessException("Invalid or unknown refresh token", HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN"));

        if (session.isRevoked() || session.getExpiresAt().isBefore(Instant.now())) {
            refreshSessionRepository.revokeFamily(session.getFamilyId());
            auditLogService.recordAuthEvent(session.getUser().getEmail(), session.getTenantId(), "TOKEN_REUSE_DETECTED", "Family: " + session.getFamilyId(), ipAddress, userAgent);
            log.error("Token reuse detected for familyId: {}. Revoking all sessions in family.", session.getFamilyId());
            throw new BusinessException("Refresh token was previously used or expired. Revoking token family.",
                    HttpStatus.UNAUTHORIZED, "TOKEN_REUSE_DETECTED");
        }

        session.setRevoked(true);

        User user = session.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("Account is disabled", HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED");
        }

        Optional<UserMembership> membershipOpt = session.getTenantId() != null
                ? membershipRepository.findByUserIdAndTenantId(user.getId(), session.getTenantId())
                : membershipRepository.findByUserIdAndTenantIdIsNull(user.getId());

        UserMembership membership = membershipOpt
                .orElseThrow(() -> new BusinessException("Membership no longer active", HttpStatus.FORBIDDEN, "MEMBERSHIP_REVOKED"));

        List<String> permissions = membership.getRole().getPermissions().stream()
                .map(Permission::getCode)
                .toList();

        String newAccessToken = jwtTokenService.generateAccessToken(
                user.getId(),
                membership.getTenantId(),
                membership.getRole().getCode(),
                permissions,
                "STAFF"
        );

        String newRawRefreshToken = generateRandomToken();
        String newTokenHash = hashToken(newRawRefreshToken);

        RefreshSession newSession = new RefreshSession();
        newSession.setUser(user);
        newSession.setTenantId(membership.getTenantId());
        newSession.setTokenHash(newTokenHash);
        newSession.setFamilyId(session.getFamilyId());
        newSession.setRevoked(false);
        newSession.setExpiresAt(Instant.now().plusSeconds(jwtProperties.getRefreshTokenExpirationSeconds()));
        newSession.setIpAddress(ipAddress);
        newSession.setUserAgent(userAgent);
        refreshSessionRepository.save(newSession);

        LoginResponse.UserProfileResponse userProfile = new LoginResponse.UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus().name(),
                membership.getTenantId(),
                membership.getRole().getCode(),
                permissions
        );

        LoginResponse loginResponse = new LoginResponse(newAccessToken, jwtProperties.getAccessTokenExpirationSeconds(), userProfile);
        return new AuthResult(loginResponse, newRawRefreshToken);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String hash = hashToken(rawRefreshToken);
            refreshSessionRepository.findByTokenHash(hash).ifPresent(session -> {
                session.setRevoked(true);
            });
        }
    }

    @Transactional
    public void logoutAll(UUID userId) {
        refreshSessionRepository.revokeAllForUser(userId);
    }

    @Transactional
    public CustomerSessionResponse createCustomerSession(CustomerSessionRequest request) {
        EntryTokenVerifier.VerifiedEntryContext entryContext = entryTokenVerifier.verifyEntryToken(request.signedEntryToken())
                .orElseThrow(() -> new BusinessException("Invalid or expired table QR entry token",
                        HttpStatus.UNAUTHORIZED, "INVALID_ENTRY_TOKEN"));

        String rawSessionToken = generateRandomToken();
        String sessionTokenHash = hashToken(rawSessionToken);
        Instant expiresAt = Instant.now().plusSeconds(14400);

        CustomerSession session = new CustomerSession();
        session.setTenantId(entryContext.tenantId());
        session.setTableNumber(entryContext.tableNumber());
        session.setCustomerName(request.name());
        session.setMaskedMobile(maskMobile(request.mobileNumber()));
        session.setSessionTokenHash(sessionTokenHash);
        session.setStatus("ACTIVE");
        session.setExpiresAt(expiresAt);
        customerSessionRepository.save(session);

        String guestJwt = jwtTokenService.generateAccessToken(
                session.getId(),
                entryContext.tenantId(),
                "CUSTOMER_GUEST",
                List.of("CUSTOMER_MENU_VIEW", "CUSTOMER_ORDER_VIEW_OWN", "CUSTOMER_ORDER_CREATE"),
                "GUEST"
        );

        return new CustomerSessionResponse(
                guestJwt,
                session.getId(),
                entryContext.tenantId(),
                entryContext.tableNumber(),
                session.getCustomerName(),
                expiresAt
        );
    }

    public static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 4) {
            return "***";
        }
        return mobile.substring(0, Math.min(mobile.length() - 4, 3)) + "****" + mobile.substring(mobile.length() - 2);
    }

    public static String generateRandomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public record AuthResult(LoginResponse loginResponse, String rawRefreshToken) {}
}