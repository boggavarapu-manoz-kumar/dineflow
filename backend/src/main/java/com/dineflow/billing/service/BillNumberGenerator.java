package com.dineflow.billing.service;

import com.dineflow.billing.repository.RestaurantBillRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class BillNumberGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RestaurantBillRepository billRepository;

    public BillNumberGenerator(RestaurantBillRepository billRepository) {
        this.billRepository = billRepository;
    }

    /**
     * Generates a collision-resistant, human-readable bill number scoped to the tenant.
     * Format: BILL-YYYYMMDD-XXXX (e.g. BILL-20261122-A8F2)
     */
    public String generateUniqueBillNumber(UUID tenantId) {
        String datePart = LocalDate.now().format(DATE_FORMAT);
        for (int attempt = 0; attempt < 10; attempt++) {
            int randomNum = RANDOM.nextInt(0x10000); // 4-char hex
            String randomPart = String.format("%04X", randomNum);
            String candidate = "BILL-" + datePart + "-" + randomPart;

            if (billRepository.findByTenantIdAndBillNumber(tenantId, candidate).isEmpty()) {
                return candidate;
            }
        }
        // High-collision fallback
        return "BILL-" + datePart + "-" + Long.toHexString(System.currentTimeMillis()).toUpperCase().substring(8);
    }
}
