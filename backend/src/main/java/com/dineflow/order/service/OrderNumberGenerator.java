package com.dineflow.order.service;

import com.dineflow.order.repository.RestaurantOrderRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class OrderNumberGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RestaurantOrderRepository orderRepository;

    public OrderNumberGenerator(RestaurantOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Generates a collision-resistant, human-readable order number scoped to the tenant.
     * Format: ORD-YYYYMMDD-XXXX (e.g. ORD-20261121-7A39)
     */
    public String generateUniqueOrderNumber(UUID tenantId) {
        String datePart = LocalDate.now().format(DATE_FORMAT);
        for (int attempt = 0; attempt < 10; attempt++) {
            int randomNum = RANDOM.nextInt(0x10000); // 4-char hex
            String randomPart = String.format("%04X", randomNum);
            String candidate = "ORD-" + datePart + "-" + randomPart;

            if (orderRepository.findByTenantIdAndOrderNumber(tenantId, candidate).isEmpty()) {
                return candidate;
            }
        }
        // Fallback with timestamp component if high collision
        return "ORD-" + datePart + "-" + Long.toHexString(System.currentTimeMillis()).toUpperCase().substring(8);
    }
}
