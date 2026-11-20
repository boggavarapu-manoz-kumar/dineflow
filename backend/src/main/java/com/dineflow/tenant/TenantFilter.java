package com.dineflow.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Filter that extracts tenant context and populates TenantContext and MDC for structured logging.
 * Guaranteed to clean up context on request completion to avoid thread-pool contamination.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantFilter extends OncePerRequestFilter {

    private static final String MDC_TENANT_KEY = "tenantId";
    private final TenantResolver tenantResolver;

    public TenantFilter(TenantResolver tenantResolver) {
        this.tenantResolver = tenantResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            Optional<UUID> tenantIdOpt = tenantResolver.resolveTenant(request);
            if (tenantIdOpt.isPresent()) {
                UUID tenantId = tenantIdOpt.get();
                TenantContext.setTenantId(tenantId);
                MDC.put(MDC_TENANT_KEY, tenantId.toString());
            } else {
                MDC.put(MDC_TENANT_KEY, "NONE");
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove(MDC_TENANT_KEY);
        }
    }
}
