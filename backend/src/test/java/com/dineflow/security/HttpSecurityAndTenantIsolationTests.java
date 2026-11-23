package com.dineflow.security;

import com.dineflow.billing.domain.BillStatus;
import com.dineflow.billing.domain.RestaurantBill;
import com.dineflow.billing.repository.RestaurantBillRepository;
import com.dineflow.order.domain.OrderStatus;
import com.dineflow.order.domain.RestaurantOrder;
import com.dineflow.order.repository.RestaurantOrderRepository;
import com.dineflow.payment.domain.PaymentMethod;
import com.dineflow.payment.domain.PaymentProviderType;
import com.dineflow.payment.domain.PaymentStatus;
import com.dineflow.payment.domain.RestaurantPayment;
import com.dineflow.payment.repository.RestaurantPaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack HTTP security integration tests exercising the REAL Spring Security filter chain,
 * real JWT validation, real @PreAuthorize method security, and real database tenant/branch isolation.
 */
@SpringBootTest
@ActiveProfiles("test")
class HttpSecurityAndTenantIsolationTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private RestaurantBillRepository billRepository;

    @Autowired
    private RestaurantPaymentRepository paymentRepository;

    @Autowired
    private RestaurantOrderRepository orderRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private String staffJwt(UUID tenantId, UUID branchId, String role, List<String> permissions) {
        return "Bearer " + jwtTokenService.generateAccessToken(
                UUID.randomUUID(), tenantId, branchId, role, permissions, "STAFF"
        );
    }

    private String guestJwt(UUID sessionId, UUID tenantId, UUID branchId) {
        return "Bearer " + jwtTokenService.generateAccessToken(
                sessionId, tenantId, branchId, "GUEST",
                List.of("CUSTOMER_MENU_VIEW", "CUSTOMER_ORDER_CREATE", "CUSTOMER_ORDER_VIEW_OWN"), "GUEST"
        );
    }

    @Test
    void unauthenticatedAndInvalidJwtRequestsAreRejectedWith401AndSecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/staff/bills"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        mockMvc.perform(get("/api/v1/payments")
                        .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJmYWtlIn0.invalid_sig"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/reports/financial"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rbacBlocksKitchenWaiterAndGuestFromUnauthorizedFinancialAndAdminEndpoints() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID branchA1 = UUID.randomUUID();

        String kitchenToken = staffJwt(tenantA, branchA1, "KITCHEN_STAFF", List.of("KITCHEN_VIEW", "KITCHEN_UPDATE"));
        String waiterToken = staffJwt(tenantA, branchA1, "WAITER", List.of("WAITER_VIEW", "ORDER_SERVE"));
        String guestToken = guestJwt(UUID.randomUUID(), tenantA, branchA1);

        // Kitchen staff CANNOT view bills, record cash, request refunds, view reports, or inspect scheduler locks
        mockMvc.perform(get("/api/v1/staff/bills").header("Authorization", kitchenToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/payments/staff/cash")
                        .header("Authorization", kitchenToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"billId\":\"" + UUID.randomUUID() + "\",\"amountReceived\":100.00}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/reports/financial").header("Authorization", kitchenToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/payments/reconciliation/locks").header("Authorization", kitchenToken))
                .andExpect(status().isForbidden());

        // Waiter CANNOT access refund administration or financial reports
        mockMvc.perform(post("/api/v1/payments/refunds/request")
                        .header("Authorization", waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentId\":\"" + UUID.randomUUID() + "\",\"amount\":50.00,\"refundMethod\":\"CASH\",\"reason\":\"test\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/reports/financial").header("Authorization", waiterToken))
                .andExpect(status().isForbidden());

        // Guest CANNOT invoke staff endpoints (cash collection, staff bills, kitchen queue, reports)
        mockMvc.perform(post("/api/v1/payments/staff/cash")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"billId\":\"" + UUID.randomUUID() + "\",\"amountReceived\":100.00}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/kitchen/queue").header("Authorization", guestToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void crossTenantCrossBranchAndCrossGuestHttpIsolationEnforcedAgainstRealDb() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID branchA1 = UUID.randomUUID();
        UUID branchA2 = UUID.randomUUID();
        UUID guest1Session = UUID.randomUUID();
        UUID guest2Session = UUID.randomUUID();

        // Persist real Tenant A Branch A1 Bill, Payment, and Order
        RestaurantBill billA1 = new RestaurantBill(tenantA, branchA1, guest1Session, "B-SEC-" + UUID.randomUUID().toString().substring(0, 6), "T-1", "INR");
        billA1.setSubtotal(new BigDecimal("500.00"));
        billA1.setGrandTotal(new BigDecimal("500.00"));
        billA1.setPaidAmount(new BigDecimal("500.00"));
        billA1.setOutstandingBalance(BigDecimal.ZERO);
        billA1.setStatus(BillStatus.PAID);
        billA1 = billRepository.save(billA1);

        RestaurantPayment payA1 = new RestaurantPayment(
                tenantA, branchA1, billA1, "PAY-SEC-" + UUID.randomUUID().toString().substring(0, 6),
                PaymentMethod.CASH, new BigDecimal("500.00"), "INR", PaymentProviderType.CASH_REGISTER);
        payA1.setPaidAmount(new BigDecimal("500.00"));
        payA1.setPaymentStatus(PaymentStatus.SUCCEEDED);
        payA1 = paymentRepository.save(payA1);

        RestaurantOrder orderGuest1 = new RestaurantOrder(tenantA, branchA1, guest1Session, "ORD-SEC-" + UUID.randomUUID().toString().substring(0, 6), "T-1");
        orderGuest1.setCurrencyCode("INR");
        orderGuest1.setSubtotal(new BigDecimal("500.00"));
        orderGuest1.setGrandTotal(new BigDecimal("500.00"));
        orderGuest1.setStatus(OrderStatus.PLACED);
        orderGuest1 = orderRepository.save(orderGuest1);

        // 1. Tenant B Hotel Admin CANNOT read Tenant A's bill or payment (404, zero data leak)
        String tenantBAdmin = staffJwt(tenantB, null, "HOTEL_ADMIN",
                List.of("BILL_VIEW", "PAYMENT_VIEW", "PAYMENT_PROCESS", "REPORT_FINANCIAL_VIEW", "HOTEL_ADMIN"));

        mockMvc.perform(get("/api/v1/staff/bills/" + billA1.getId()).header("Authorization", tenantBAdmin))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString(billA1.getBillNumber()))));

        mockMvc.perform(get("/api/v1/payments/" + payA1.getId()).header("Authorization", tenantBAdmin))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString(payA1.getPaymentReference()))));

        // 2. Tenant A Branch A2 Manager CANNOT reverse Tenant A Branch A1's payment (403 CROSS_BRANCH_ACCESS)
        String branchA2Manager = staffJwt(tenantA, branchA2, "BRANCH_MANAGER",
                List.of("BILL_VIEW", "PAYMENT_VIEW", "PAYMENT_PROCESS", "REFUND_APPROVE"));

        mockMvc.perform(post("/api/v1/payments/" + payA1.getId() + "/reverse")
                        .header("Authorization", branchA2Manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Unauthorized cross-branch attempt\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CROSS_BRANCH_ACCESS"));

        // 3. Guest 2 in Tenant A CANNOT read Guest 1's order (403 ORDER_ACCESS_DENIED)
        String guest2Token = guestJwt(guest2Session, tenantA, branchA1);
        mockMvc.perform(get("/api/v1/customer/orders/" + orderGuest1.getId()).header("Authorization", guest2Token))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString(orderGuest1.getOrderNumber()))));

        // 4. Guest 1 CAN read own order (200 OK)
        String guest1Token = guestJwt(guest1Session, tenantA, branchA1);
        mockMvc.perform(get("/api/v1/customer/orders/" + orderGuest1.getId()).header("Authorization", guest1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNumber").value(orderGuest1.getOrderNumber()));
    }

    @Test
    void malformedJsonInvalidWebhookSignatureAndErrorResponsesDoNotLeakInternals() throws Exception {
        String staffToken = staffJwt(UUID.randomUUID(), UUID.randomUUID(), "CASHIER", List.of("PAYMENT_PROCESS"));

        // Malformed JSON -> 400 INVALID_REQUEST_PAYLOAD, no Java stack trace or exception class leaked
        mockMvc.perform(post("/api/v1/payments/initiate")
                        .header("Authorization", staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-valid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_PAYLOAD"))
                .andExpect(content().string(not(containsString("JsonParseException"))))
                .andExpect(content().string(not(containsString("com.dineflow"))));

        // Invalid webhook signature -> 400 INVALID_WEBHOOK_SIGNATURE
        mockMvc.perform(post("/api/v1/payments/webhook/MOCK")
                        .header("X-Razorpay-Signature", "INVALID_SIG")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event\":\"payment.captured\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_WEBHOOK_SIGNATURE"));
    }
}
