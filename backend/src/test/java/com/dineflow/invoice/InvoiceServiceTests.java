package com.dineflow.invoice;

import com.dineflow.billing.domain.BillStatus;
import com.dineflow.billing.domain.RestaurantBill;
import com.dineflow.billing.domain.RestaurantBillItem;
import com.dineflow.billing.domain.RestaurantBillItemOption;
import com.dineflow.billing.repository.RestaurantBillRepository;
import com.dineflow.branch.domain.Branch;
import com.dineflow.branch.repository.BranchRepository;
import com.dineflow.common.exception.BusinessException;
import com.dineflow.invoice.dto.CancelInvoiceRequest;
import com.dineflow.invoice.dto.GenerateInvoiceRequest;
import com.dineflow.invoice.dto.InvoiceResponse;
import com.dineflow.invoice.repository.FinancialDocumentRecordRepository;
import com.dineflow.invoice.repository.RestaurantInvoiceRepository;
import com.dineflow.invoice.service.InvoiceService;
import com.dineflow.order.domain.OrderStatus;
import com.dineflow.order.domain.RestaurantOrder;
import com.dineflow.order.repository.RestaurantOrderRepository;
import com.dineflow.security.DineFlowPrincipal;
import com.dineflow.tenant.domain.HotelProfile;
import com.dineflow.tenant.domain.Tenant;
import com.dineflow.tenant.repository.HotelProfileRepository;
import com.dineflow.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class InvoiceServiceTests {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private RestaurantInvoiceRepository invoiceRepository;

    @Autowired
    private FinancialDocumentRecordRepository documentRecordRepository;

    @Autowired
    private RestaurantBillRepository billRepository;

    @Autowired
    private RestaurantOrderRepository orderRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private HotelProfileRepository hotelProfileRepository;

    private UUID tenantId;
    private UUID branchId;
    private UUID sessionId;
    private UUID staffId;
    private DineFlowPrincipal staffPrincipal;
    private RestaurantBill bill;

    @BeforeEach
    void setUp() {
        documentRecordRepository.deleteAll();
        invoiceRepository.deleteAll();
        billRepository.deleteAll();
        orderRepository.deleteAll();
        hotelProfileRepository.deleteAll();
        branchRepository.deleteAll();
        tenantRepository.deleteAll();

        // 1. Seed Tenant
        Tenant tenant = new Tenant();
        tenant.setSlug("oberoi-grand-" + UUID.randomUUID());
        tenant.setBusinessName("The Oberoi Grand");
        tenant.setStatus("ACTIVE");
        tenant.setSubscriptionPlan("ENTERPRISE");
        tenant = tenantRepository.save(tenant);
        tenantId = tenant.getId();

        sessionId = UUID.randomUUID();
        staffId = UUID.randomUUID();

        // 2. Seed Branch
        Branch branch = new Branch("Banquets & Terrace", "MAIN", "15 Jawaharlal Nehru Rd, Kolkata", "+91 33 2249 2323", true, true);
        branch.setTenantId(tenantId);
        branch = branchRepository.save(branch);
        branchId = branch.getId();

        // 3. Seed Hotel Profile
        HotelProfile profile = new HotelProfile();
        profile.setTenantId(tenantId);
        profile.setCurrencyCode("INR");
        profile.setContactEmail("accounts@oberoigrand.com");
        profile.setTaxPercentage(new BigDecimal("18.00"));
        profile.setServiceChargePercentage(new BigDecimal("10.00"));
        hotelProfileRepository.save(profile);

        // 4. Seed Bill
        bill = new RestaurantBill(tenantId, branchId, sessionId, "BILL-20261122-TEST", "Table-12", "The Oberoi Grand");
        bill.setBranchNameSnapshot("Banquets & Terrace");
        bill.setBranchAddressSnapshot("15 Jawaharlal Nehru Rd, Kolkata");
        bill.setCurrencyCode("INR");
        bill.setSubtotal(new BigDecimal("1000.00"));
        bill.setDiscountAmount(new BigDecimal("100.00"));
        bill.setServiceChargePercentage(new BigDecimal("10.00"));
        bill.setServiceChargeAmount(new BigDecimal("90.00")); // 10% of 900
        bill.setTaxPercentage(new BigDecimal("18.00"));
        bill.setTaxAmount(new BigDecimal("178.20")); // 18% of 990
        bill.setGrandTotal(new BigDecimal("1168.20"));
        bill.setPaidAmount(new BigDecimal("1168.20"));
        bill.setOutstandingBalance(BigDecimal.ZERO);
        bill.setStatus(BillStatus.PAID);

        // Create an order & item for PDF rendering test
        RestaurantOrder order = new RestaurantOrder(tenantId, branchId, sessionId, "Table-12", "ORD-20261122-008");
        order.setStatus(OrderStatus.SERVED);
        order.setSubtotal(new BigDecimal("1000.00"));
        order.setGrandTotal(new BigDecimal("1168.20"));
        order = orderRepository.save(order);

        RestaurantBillItem item = new RestaurantBillItem(
                bill, order, UUID.randomUUID(), "Smoked Himalayan Trout", "Chef Signature",
                "Wild trout with fresh herbs and butter sauce", 2, new BigDecimal("500.00"),
                BigDecimal.ZERO, new BigDecimal("500.00"), new BigDecimal("1000.00"), "Well done"
        );
        bill.addItem(item);
        bill = billRepository.save(bill);

        staffPrincipal = new DineFlowPrincipal(staffId, tenantId, branchId, "HOTEL_ADMIN", List.of("INVOICE_GENERATE", "INVOICE_VIEW", "INVOICE_CANCEL"), "STAFF");
    }

    @Test
    @DisplayName("Should successfully generate invoice from bill and persist PDF binary")
    void shouldGenerateInvoiceAndStorePdf() {
        GenerateInvoiceRequest req = new GenerateInvoiceRequest(
                bill.getId(), "Dr. A. Sen", "TAX-ID-992211", "+91 9830011223",
                "CREDIT_CARD", "AUTH-987654", "Corporate Guest Dining"
        );

        InvoiceResponse response = invoiceService.generateInvoiceForBill(staffPrincipal, req);

        assertThat(response).isNotNull();
        assertThat(response.invoiceNumber()).startsWith("INV-");
        assertThat(response.status()).isEqualTo("ISSUED");
        assertThat(response.customerName()).isEqualTo("Dr. A. Sen");
        assertThat(response.customerTaxId()).isEqualTo("TAX-ID-992211");
        assertThat(response.restaurantName()).isEqualTo("The Oberoi Grand");
        assertThat(response.branchName()).isEqualTo("Banquets & Terrace");
        assertThat(response.grandTotal()).isEqualByComparingTo("1168.20");
        assertThat(response.paidAmount()).isEqualByComparingTo("1168.20");
        assertThat(response.paymentMethod()).isEqualTo("CREDIT_CARD");

        // Verify PDF was generated and can be loaded
        Resource pdf = invoiceService.getInvoicePdfResource(staffPrincipal, response.id());
        assertThat(pdf).isNotNull();
        assertThat(pdf.exists()).isTrue();
    }

    @Test
    @DisplayName("Should prevent cross-tenant invoice download or access")
    void shouldPreventCrossTenantAccess() {
        GenerateInvoiceRequest req = new GenerateInvoiceRequest(bill.getId(), null, null, null, null, null, null);
        InvoiceResponse invoice = invoiceService.generateInvoiceForBill(staffPrincipal, req);

        UUID foreignTenantId = UUID.randomUUID();
        DineFlowPrincipal intruderPrincipal = new DineFlowPrincipal(staffId, foreignTenantId, branchId, "HOTEL_ADMIN", List.of("INVOICE_VIEW"), "STAFF");

        assertThatThrownBy(() -> invoiceService.getInvoice(intruderPrincipal, invoice.id()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not found");

        assertThatThrownBy(() -> invoiceService.getInvoicePdfResource(intruderPrincipal, invoice.id()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("Should cancel an invoice with mandatory audit reason")
    void shouldCancelInvoiceWithReason() {
        GenerateInvoiceRequest req = new GenerateInvoiceRequest(bill.getId(), null, null, null, null, null, null);
        InvoiceResponse invoice = invoiceService.generateInvoiceForBill(staffPrincipal, req);

        CancelInvoiceRequest cancelReq = new CancelInvoiceRequest("Incorrect customer corporate GSTIN provided");
        InvoiceResponse cancelled = invoiceService.cancelInvoice(staffPrincipal, invoice.id(), cancelReq);

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.cancellationReason()).isEqualTo("Incorrect customer corporate GSTIN provided");
        assertThat(cancelled.cancelledAt()).isNotNull();
    }
}
