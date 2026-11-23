package com.dineflow.config;

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
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Assigns a sanitized, traceable X-Correlation-ID to every incoming HTTP request,
 * populates SLF4J MDC for structured logging (preventing log-forging injection),
 * and returns it in response headers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("^[a-zA-Z0-9_-]{4,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String incoming = request.getHeader(CORRELATION_HEADER);
        String correlationId = isSafe(incoming) ? incoming.trim() : UUID.randomUUID().toString();

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(CORRELATION_HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    public static boolean isSafe(String value) {
        return value != null && SAFE_CORRELATION_ID.matcher(value.trim()).matches();
    }

    public static String currentOrNew(String prefix) {
        String existing = MDC.get(MDC_KEY);
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 12);
    }
}
