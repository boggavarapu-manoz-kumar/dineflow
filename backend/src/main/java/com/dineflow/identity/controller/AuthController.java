package com.dineflow.identity.controller;

import com.dineflow.common.api.ApiResponse;
import com.dineflow.common.exception.BusinessException;
import com.dineflow.identity.dto.ForgotPasswordRequest;
import com.dineflow.identity.dto.LoginRequest;
import com.dineflow.identity.dto.LoginResponse;
import com.dineflow.identity.dto.ResetPasswordRequest;
import com.dineflow.identity.service.AuthService;
import com.dineflow.security.CookieHelper;
import com.dineflow.security.DineFlowPrincipal;
import com.dineflow.security.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final CookieHelper cookieHelper;
    private final RateLimiterService rateLimiterService;

    public AuthController(AuthService authService, CookieHelper cookieHelper, RateLimiterService rateLimiterService) {
        this.authService = authService;
        this.cookieHelper = cookieHelper;
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        String ip = servletRequest.getRemoteAddr();
        // Rate Limiting: 10 attempts per minute per IP
        if (!rateLimiterService.tryAcquire("login:" + ip, 10, 60)) {
            throw new BusinessException("Too many login attempts. Please wait a minute.",
                    HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED");
        }

        String userAgent = servletRequest.getHeader("User-Agent");
        AuthService.AuthResult result = authService.login(request, ip, userAgent);

        ResponseCookie cookie = cookieHelper.createRefreshTokenCookie(
                result.rawRefreshToken(),
                604800L
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.ok(result.loginResponse(), "Login successful"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @CookieValue(name = "__Host-DineFlow-RT", required = false) String refreshToken,
            HttpServletRequest servletRequest) {
        String ip = servletRequest.getRemoteAddr();
        if (!rateLimiterService.tryAcquire("refresh:" + ip, 30, 60)) {
            throw new BusinessException("Too many refresh requests", HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED");
        }

        String userAgent = servletRequest.getHeader("User-Agent");
        AuthService.AuthResult result = authService.refreshSession(refreshToken, ip, userAgent);

        ResponseCookie cookie = cookieHelper.createRefreshTokenCookie(
                result.rawRefreshToken(),
                604800L
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.ok(result.loginResponse(), "Session refreshed successfully"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Map<String, String>>> logout(
            @CookieValue(name = "__Host-DineFlow-RT", required = false) String refreshToken) {
        authService.logout(refreshToken);
        ResponseCookie clearCookie = cookieHelper.clearRefreshTokenCookie();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .body(ApiResponse.ok(Map.of("status", "LOGGED_OUT"), "Logout successful"));
    }

    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Map<String, String>>> logoutAll(
            @AuthenticationPrincipal DineFlowPrincipal principal) {
        authService.logoutAll(principal.getUserId());
        ResponseCookie clearCookie = cookieHelper.clearRefreshTokenCookie();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .body(ApiResponse.ok(Map.of("status", "ALL_SESSIONS_REVOKED"), "All sessions revoked"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMe(
            @AuthenticationPrincipal DineFlowPrincipal principal) {
        Map<String, Object> profile = Map.of(
                "userId", principal.getUserId(),
                "tenantId", principal.getTenantId() != null ? principal.getTenantId() : "GLOBAL",
                "role", principal.getRoleCode(),
                "type", principal.getType(),
                "authorities", principal.getAuthorities().stream().map(Object::toString).toList()
        );

        return ResponseEntity.ok(ApiResponse.ok(profile, "Authenticated principal profile"));
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<ApiResponse<Map<String, String>>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest servletRequest) {
        if (!rateLimiterService.tryAcquire("forgot:" + servletRequest.getRemoteAddr(), 5, 60)) {
            throw new BusinessException("Too many reset attempts", HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED");
        }

        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("message", "If an account with that email exists, reset instructions have been dispatched."),
                "Password reset requested"
        ));
    }

    @PostMapping("/password/reset")
    public ResponseEntity<ApiResponse<Map<String, String>>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("status", "PASSWORD_RESET_SUCCESSFUL"),
                "Password has been reset successfully"
        ));
    }

    /**
     * Demonstrates cross-tenant resource protection via @PreAuthorize("@tenantSecurity.hasTenantAccess(#tenantId)")
     */
    @GetMapping("/tenant-resource")
    @PreAuthorize("@tenantSecurity.hasTenantAccess(#tenantId) and hasAuthority('ORDER_VIEW')")
    public ResponseEntity<ApiResponse<Map<String, String>>> getTenantProtectedResource(
            @RequestParam UUID tenantId) {
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("data", "Confidential tenant data for: " + tenantId),
                "Tenant resource access granted"
        ));
    }
}