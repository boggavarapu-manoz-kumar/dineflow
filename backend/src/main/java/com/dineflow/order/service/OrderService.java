package com.dineflow.order.service;

import com.dineflow.cart.domain.CustomerCart;
import com.dineflow.cart.domain.CustomerCartItem;
import com.dineflow.cart.repository.CustomerCartRepository;
import com.dineflow.common.exception.BusinessException;
import com.dineflow.identity.domain.CustomerSession;
import com.dineflow.identity.repository.CustomerSessionRepository;
import com.dineflow.menu.domain.MenuItem;
import com.dineflow.menu.repository.MenuItemRepository;
import com.dineflow.order.domain.OrderIdempotencyRecord;
import com.dineflow.order.domain.OrderStatus;
import com.dineflow.order.domain.RestaurantOrder;
import com.dineflow.order.domain.RestaurantOrderItem;
import com.dineflow.order.domain.RestaurantOrderItemOption;
import com.dineflow.order.domain.RestaurantOrderStatusHistory;
import com.dineflow.order.dto.CreateOrderRequest;
import com.dineflow.order.dto.OrderResponse;
import com.dineflow.order.repository.OrderIdempotencyRepository;
import com.dineflow.order.repository.RestaurantOrderRepository;
import com.dineflow.pricing.dto.PricingBreakdown;
import com.dineflow.pricing.service.OrderPricingEngine;
import com.dineflow.security.DineFlowPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final RestaurantOrderRepository orderRepository;
    private final OrderIdempotencyRepository idempotencyRepository;
    private final CustomerCartRepository cartRepository;
    private final CustomerSessionRepository sessionRepository;
    private final MenuItemRepository menuItemRepository;
    private final OrderPricingEngine pricingEngine;
    private final OrderNumberGenerator orderNumberGenerator;
    private final ObjectMapper objectMapper;

    public OrderService(RestaurantOrderRepository orderRepository,
            OrderIdempotencyRepository idempotencyRepository,
            CustomerCartRepository cartRepository,
            CustomerSessionRepository sessionRepository,
            MenuItemRepository menuItemRepository,
            OrderPricingEngine pricingEngine,
            OrderNumberGenerator orderNumberGenerator) {
        this.orderRepository = orderRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.cartRepository = cartRepository;
        this.sessionRepository = sessionRepository;
        this.menuItemRepository = menuItemRepository;
        this.pricingEngine = pricingEngine;
        this.orderNumberGenerator = orderNumberGenerator;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    /**
     * Creates a restaurant order idempotently and transactionally from the
     * customer's cart.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderResponse createOrderFromCart(DineFlowPrincipal principal, String idempotencyKey,
            CreateOrderRequest request) {
        UUID tenantId = principal.getTenantId();
        UUID sessionId = principal.getUserId();

        // 1. Authenticate & validate CustomerSession from database
        CustomerSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException("Customer dining session invalid or expired",
                        HttpStatus.UNAUTHORIZED, "SESSION_INVALID"));

        if (!"ACTIVE".equals(session.getStatus())) {
            throw new BusinessException("Customer session is inactive", HttpStatus.FORBIDDEN, "SESSION_INACTIVE");
        }
        if (session.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("Customer session has expired", HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED");
        }
        if (!session.getTenantId().equals(tenantId)) {
            throw new BusinessException("Cross-tenant session access forbidden", HttpStatus.FORBIDDEN,
                    "CROSS_TENANT_ACCESS");
        }

        UUID branchId = session.getBranchId() != null ? session.getBranchId() : principal.getBranchId();
        String tableNumber = session.getTableNumber();

        // 2. Compute request payload hash for idempotency protection
        String requestPayloadForHash = (request != null
                ? (request.specialNotes() != null ? request.specialNotes() : "") +
                        ":" + (request.expectedGrandTotal() != null ? request.expectedGrandTotal().toPlainString() : "")
                : "");
        String requestHash = computeSha256(requestPayloadForHash);

        // 3. Handle Idempotency check if idempotencyKey is provided
        OrderIdempotencyRecord idempotencyRecord = null;
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<OrderIdempotencyRecord> existingOpt = idempotencyRepository
                    .findByCustomerSessionIdAndIdempotencyKey(sessionId, idempotencyKey);
            if (existingOpt.isPresent()) {
                OrderIdempotencyRecord existing = existingOpt.get();
                // If payload hash doesn't match, reject reusing key for a different payload
                if (!existing.getRequestHash().equals(requestHash)) {
                    throw new BusinessException("Idempotency key reuse with different request payload is rejected",
                            HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_PAYLOAD_MISMATCH");
                }

                if ("COMPLETED".equals(existing.getStatus()) && existing.getOrderId() != null) {
                    RestaurantOrder existingOrder = orderRepository.findByIdAndTenantId(existing.getOrderId(), tenantId)
                            .orElseThrow(() -> new BusinessException("Order record not found",
                                    HttpStatus.INTERNAL_SERVER_ERROR, "ORDER_NOT_FOUND"));
                    return toOrderResponse(existingOrder);
                } else if ("IN_PROGRESS".equals(existing.getStatus())) {
                    throw new BusinessException(
                            "Order submission is currently in progress for this request. Please wait.",
                            HttpStatus.CONFLICT, "ORDER_IN_PROGRESS");
                }
            }

            try {
                idempotencyRecord = new OrderIdempotencyRecord(
                        tenantId,
                        sessionId,
                        idempotencyKey,
                        requestHash,
                        Instant.now().plus(24, ChronoUnit.HOURS));
                idempotencyRecord = idempotencyRepository.saveAndFlush(idempotencyRecord);
            } catch (DataIntegrityViolationException ex) {
                // Concurrent submission collided on the unique constraint
                throw new BusinessException("Concurrent order submission detected. Please wait.",
                        HttpStatus.CONFLICT, "CONCURRENT_ORDER_SUBMISSION");
            }
        }

        // 4. Retrieve current active cart
        CustomerCart cart = cartRepository.findByCustomerSessionIdAndTenantId(sessionId, tenantId)
                .orElseThrow(() -> new BusinessException("Cart not found", HttpStatus.NOT_FOUND, "CART_NOT_FOUND"));

        if (cart.getItems().isEmpty()) {
            if (idempotencyRecord != null) {
                idempotencyRecord.setStatus("FAILED");
                idempotencyRepository.save(idempotencyRecord);
            }
            throw new BusinessException("Cannot create an order with an empty cart", HttpStatus.BAD_REQUEST,
                    "CART_EMPTY");
        }

        // 5. Authoritatively calculate and validate all item prices, availability, and
        // totals
        PricingBreakdown pricing;
        try {
            pricing = pricingEngine.calculateCart(cart);
        } catch (Exception ex) {
            if (idempotencyRecord != null) {
                idempotencyRecord.setStatus("FAILED");
                idempotencyRepository.save(idempotencyRecord);
            }
            throw ex;
        }

        // 6. Verify client expected total against authoritative total (if client passed
        // expected total)
        if (request != null && request.expectedGrandTotal() != null) {
            java.math.BigDecimal clientExpected = request.expectedGrandTotal().setScale(2,
                    java.math.RoundingMode.HALF_UP);
            if (!clientExpected.equals(pricing.grandTotal())) {
                if (idempotencyRecord != null) {
                    idempotencyRecord.setStatus("FAILED");
                    idempotencyRepository.save(idempotencyRecord);
                }
                throw new BusinessException(
                        "Order total has changed from " + pricing.currencyCode() + " " + clientExpected +
                                " to " + pricing.currencyCode() + " " + pricing.grandTotal()
                                + ". Please review and re-submit.",
                        HttpStatus.CONFLICT, "PRICE_CHANGED");
            }
        }

        // 7. Create RestaurantOrder entity
        String orderNumber = orderNumberGenerator.generateUniqueOrderNumber(tenantId);
        RestaurantOrder order = new RestaurantOrder(tenantId, branchId, sessionId, tableNumber, orderNumber);
        order.setCurrencyCode(pricing.currencyCode());
        order.setTotalItemCount(pricing.totalItemCount());
        order.setSubtotal(pricing.itemsSubtotal());
        order.setTaxPercentage(pricing.taxPercentage());
        order.setTaxAmount(pricing.taxAmount());
        order.setServiceChargePercentage(pricing.serviceChargePercentage());
        order.setServiceChargeAmount(pricing.serviceChargeAmount());
        order.setGrandTotal(pricing.grandTotal());
        order.setSpecialNotes(request != null ? request.specialNotes() : null);
        order.setCartId(cart.getId());

        // 8. Create historical snapshot line items & options
        Map<UUID, MenuItem> menuItemCache = menuItemRepository.findAllById(
                pricing.lineItems().stream().map(PricingBreakdown.CalculatedLineItem::menuItemId)
                        .collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(MenuItem::getId, m -> m));

        for (PricingBreakdown.CalculatedLineItem lineCalc : pricing.lineItems()) {
            CustomerCartItem matchingCartItem = cart.getItems().stream()
                    .filter(ci -> ci.getId().equals(lineCalc.cartItemId()))
                    .findFirst()
                    .orElse(null);

            MenuItem mi = menuItemCache.get(lineCalc.menuItemId());
            String itemDescription = mi != null ? mi.getShortDescription() : null;

            RestaurantOrderItem orderItem = new RestaurantOrderItem(
                    order,
                    lineCalc.menuItemId(),
                    lineCalc.variantId(),
                    lineCalc.itemName(),
                    lineCalc.variantName(),
                    itemDescription,
                    lineCalc.quantity(),
                    lineCalc.basePrice(),
                    lineCalc.optionsSubtotal(),
                    lineCalc.effectiveUnitPrice(),
                    lineCalc.lineTotal(),
                    matchingCartItem != null ? matchingCartItem.getSpecialInstructions() : null);

            // Snapshot options
            for (PricingBreakdown.CalculatedOption optCalc : lineCalc.selectedOptions()) {
                RestaurantOrderItemOption itemOption = new RestaurantOrderItemOption(
                        orderItem,
                        optCalc.optionId(),
                        optCalc.optionName(),
                        optCalc.additionalPrice());
                orderItem.addOption(itemOption);
            }

            order.addItem(orderItem);
        }

        // 9. Record initial status history
        RestaurantOrderStatusHistory initialHistory = new RestaurantOrderStatusHistory(
                order,
                null,
                OrderStatus.PLACED.name(),
                sessionId,
                "CUSTOMER_GUEST",
                "GUEST",
                "Order created by customer via QR digital session");
        order.addStatusHistory(initialHistory);

        // 10. Persist order
        RestaurantOrder savedOrder = orderRepository.save(order);

        // 11. Clear / convert cart items to prevent duplicate checkout of the same cart
        // items
        cart.clearItems();
        cartRepository.save(cart);

        // 12. Update Idempotency Record if present
        OrderResponse response = toOrderResponse(savedOrder);
        if (idempotencyRecord != null) {
            idempotencyRecord.setStatus("COMPLETED");
            idempotencyRecord.setOrderId(savedOrder.getId());
            try {
                idempotencyRecord.setResponsePayload(objectMapper.writeValueAsString(response));
            } catch (JsonProcessingException e) {
                log.warn("Could not serialize order response for idempotency caching: {}", e.getMessage());
            }
            idempotencyRepository.save(idempotencyRecord);
        }

        log.info("Order placed successfully. Tenant: {}, OrderId: {}, OrderNumber: {}, Amount: {} {}",
                tenantId, savedOrder.getId(), savedOrder.getOrderNumber(), savedOrder.getCurrencyCode(),
                savedOrder.getGrandTotal());

        return response;
    }

    @Transactional(readOnly = true)
    public OrderResponse getCustomerOrder(DineFlowPrincipal principal, UUID orderId) {
        UUID tenantId = principal.getTenantId();
        UUID sessionId = principal.getUserId();

        RestaurantOrder order = orderRepository.findByIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> new BusinessException("Order not found", HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND"));

        // Only allow customer to view their own session's order (or staff with
        // permissions)
        if ("GUEST".equals(principal.getType()) && !order.getCustomerSessionId().equals(sessionId)) {
            throw new BusinessException("Access denied: You do not own this order", HttpStatus.FORBIDDEN,
                    "ORDER_ACCESS_DENIED");
        }

        return toOrderResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getCustomerOrders(DineFlowPrincipal principal) {
        UUID tenantId = principal.getTenantId();
        UUID sessionId = principal.getUserId();

        List<RestaurantOrder> orders = orderRepository
                .findAllByCustomerSessionIdAndTenantIdOrderByCreatedAtDesc(sessionId, tenantId);
        return orders.stream().map(this::toOrderResponse).toList();
    }

    public OrderResponse toOrderResponse(RestaurantOrder order) {
        List<OrderResponse.OrderItemResponse> itemResponses = new ArrayList<>();
        if (order.getItems() != null) {
            for (RestaurantOrderItem item : order.getItems()) {
                List<OrderResponse.OrderItemOptionResponse> optResponses = new ArrayList<>();
                if (item.getOptions() != null) {
                    for (RestaurantOrderItemOption opt : item.getOptions()) {
                        optResponses.add(new OrderResponse.OrderItemOptionResponse(
                                opt.getOptionId(),
                                opt.getOptionNameSnapshot(),
                                opt.getPriceContribution()));
                    }
                }

                itemResponses.add(new OrderResponse.OrderItemResponse(
                        item.getId(),
                        item.getMenuItemId(),
                        item.getItemNameSnapshot(),
                        item.getVariantId(),
                        item.getVariantNameSnapshot(),
                        item.getQuantity(),
                        item.getBaseUnitPrice(),
                        item.getOptionsUnitPrice(),
                        item.getEffectiveUnitPrice(),
                        item.getLineTotal(),
                        item.getSpecialInstructions(),
                        optResponses));
            }
        }

        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getTenantId(),
                order.getBranchId(),
                order.getTableNumber(),
                order.getCurrencyCode(),
                order.getTotalItemCount(),
                order.getSubtotal(),
                order.getTaxPercentage(),
                order.getTaxAmount(),
                order.getServiceChargePercentage(),
                order.getServiceChargeAmount(),
                order.getGrandTotal(),
                order.getSpecialNotes(),
                order.getAssignedWaiterId(),
                order.getCreatedAt(),
                itemResponses);
    }

    private static String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
