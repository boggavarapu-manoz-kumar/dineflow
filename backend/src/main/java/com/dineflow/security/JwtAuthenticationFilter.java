package com.dineflow.security;

import com.dineflow.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            Optional<Claims> claimsOpt = jwtTokenService.validateAndParseClaims(token);

            if (claimsOpt.isPresent()) {
                Claims claims = claimsOpt.get();
                UUID userId = UUID.fromString(claims.getSubject());
                String tenantIdStr = claims.get("tenantId", String.class);
                UUID tokenTenantId = tenantIdStr != null ? UUID.fromString(tenantIdStr) : null;
                String roleCode = claims.get("role", String.class);
                String type = claims.get("type", String.class);
                
                @SuppressWarnings("unchecked")
                List<String> perms = (List<String>) claims.get("permissions", List.class);
                if (perms == null) {
                    perms = List.of();
                }

                // If TenantContext is already established (e.g. via X-Tenant-ID header) and token is tenant-scoped,
                // verify that the token matches the requested tenant (prevent cross-tenant token replay)
                Optional<UUID> currentTenantOpt = TenantContext.getTenantId();
                if (currentTenantOpt.isPresent() && tokenTenantId != null && !currentTenantOpt.get().equals(tokenTenantId)) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Cross-tenant access forbidden");
                    return;
                }

                // If TenantContext was not set by header, populate it from trusted token claim
                if (currentTenantOpt.isEmpty() && tokenTenantId != null) {
                    TenantContext.setTenantId(tokenTenantId);
                }

                DineFlowPrincipal principal = new DineFlowPrincipal(userId, tokenTenantId, roleCode, perms, type);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}