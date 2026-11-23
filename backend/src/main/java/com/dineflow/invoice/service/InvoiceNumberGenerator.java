package com.dineflow.invoice.service;

import com.dineflow.invoice.repository.RestaurantInvoiceRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class InvoiceNumberGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RestaurantInvoiceRepository invoiceRepository;

    public InvoiceNumberGenerator(RestaurantInvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    /**
     * Generates a unique, collision-resistant invoice identifier scoped to the tenant in UTC.
     * Format: INV-YYYYMMDD-XXXX (e.g. INV-20261122-8B29)
     */
    public String generateUniqueInvoiceNumber(UUID tenantId) {
        String datePart = LocalDate.now(ZoneOffset.UTC).format(DATE_FORMAT);
        for (int attempt = 0; attempt < 10; attempt++) {
            int randomNum = RANDOM.nextInt(0x10000); // 4-char hex
            String randomPart = String.format("%04X", randomNum);
            String candidate = "INV-" + datePart + "-" + randomPart;

            if (invoiceRepository.findByTenantIdAndInvoiceNumber(tenantId, candidate).isEmpty()) {
                return candidate;
            }
        }
        return "INV-" + datePart + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
