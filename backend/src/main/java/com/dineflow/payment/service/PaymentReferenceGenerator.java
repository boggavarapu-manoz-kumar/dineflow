package com.dineflow.payment.service;

import com.dineflow.payment.repository.RestaurantPaymentRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class PaymentReferenceGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RestaurantPaymentRepository paymentRepository;

    public PaymentReferenceGenerator(RestaurantPaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    /**
     * Generates a collision-resistant, human-readable payment reference scoped to the tenant.
     * Format: PAY-YYYYMMDD-XXXX (e.g. PAY-20261122-4F11)
     */
    public String generateUniquePaymentReference(UUID tenantId) {
        String datePart = LocalDate.now().format(DATE_FORMAT);
        for (int attempt = 0; attempt < 10; attempt++) {
            int randomNum = RANDOM.nextInt(0x10000); // 4-char hex
            String randomPart = String.format("%04X", randomNum);
            String candidate = "PAY-" + datePart + "-" + randomPart;

            if (paymentRepository.findByTenantIdAndPaymentReference(tenantId, candidate).isEmpty()) {
                return candidate;
            }
        }
        return "PAY-" + datePart + "-" + Long.toHexString(System.currentTimeMillis()).toUpperCase().substring(8);
    }
}
