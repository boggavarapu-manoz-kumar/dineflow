package com.dineflow.billing.service;

import com.dineflow.billing.domain.BillOrderMapping;
import com.dineflow.billing.domain.BillStatus;
import com.dineflow.billing.domain.RestaurantBill;
import com.dineflow.billing.domain.RestaurantBillItem;
import com.dineflow.billing.domain.RestaurantBillItemOption;
import com.dineflow.billing.domain.RestaurantBillStatusHistory;
import com.dineflow.billing.dto.BillResponse;
import com.dineflow.billing.dto.GenerateBillRequest;
import com.dineflow.billing.dto.VoidBillRequest;
import com.dineflow.billing.repository.RestaurantBillRepository;
import com.dineflow.branch.domain.Branch;
import com.dineflow.branch.repository.BranchRepository;
import com.dineflow.common.exception.BusinessException;
import com.dineflow.order.domain.OrderStatus;
import com.dineflow.order.domain.RestaurantOrder;
import com.dineflow.order.domain.RestaurantOrderItem;
import com.dineflow.order.domain.RestaurantOrderItemOption;
import com.dineflow.order.repository.RestaurantOrderRepository;
import com.dineflow.security.DineFlowPrincipal;
import com.dineflow.tenant.domain.HotelProfile;
import com.dineflow.tenant.domain.Tenant;
import com.dineflow.tenant.repository.HotelProfileRepository;
import com.dineflow.tenant.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class BillingEngineService {

    private static final Logger log = LoggerFactory.getLogger(BillingEngineService.class);

    private final RestaurantBillRepository billRepository;
    private final RestaurantOrderRepository orderRepository;
    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final HotelProfileRepository hotelProfileRepository;
    private final BillNumberGenerator billNumberGenerator;

    public BillingEngineService(RestaurantBillRepository billRepository,
                                RestaurantOrderRepository orderRepository,
                                TenantRepository tenantRepository,
                                BranchRepository branchRepository,
                                HotelProfileRepository hotelProfileRepository,
                                BillNumberGenerator billNumberGenerator) {
        this.billRepository = billRepository;
        this.orderRepository = orderRepository;
        this.tenantRepository = tenantRepository;
        this.branchRepository = branchRepository;
        this.hotelProfileRepository = hotelProfileRepository;
        this.billNumberGenerator = billNumberGenerator;
    }

    /**
     * Generates an authoritative restaurant bill from one or more confirmed orders.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BillResponse generateBillForOrders(DineFlowPrincipal principal, GenerateBillRequest request) {
        UUID tenantId = principal.getTenantId();
        if (request == null || request.orderIds() == null || request.orderIds().isEmpty()) {
            throw new BusinessException("At least one order ID is required to generate a bill", HttpStatus.BAD_REQUEST, "ORDER_IDS_REQUIRED");
        }

        // 1. Load orders and validate
        List<RestaurantOrder> orders = orderRepository.findAllById(request.orderIds());
        if (orders.isEmpty() || orders.size() != request.orderIds().size()) {
            throw new BusinessException("One or more specified orders could not be found", HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND");
        }

        // Multi-tenant & branch verification
        UUID commonBranchId = null;
        String commonTableNumber = null;
        UUID commonCustomerSessionId = null;

        for (RestaurantOrder o : orders) {
            if (!o.getTenantId().equals(tenantId)) {
                throw new BusinessException("Unauthorized cross-tenant order access", HttpStatus.FORBIDDEN, "CROSS_TENANT_ACCESS");
            }
            if (principal.getBranchId() != null && o.getBranchId() != null && !o.getBranchId().equals(principal.getBranchId())) {
                throw new BusinessException("Unauthorized cross-branch order access", HttpStatus.FORBIDDEN, "CROSS_BRANCH_ACCESS");
            }

            // Must not be cancelled
            if (o.getStatus() == OrderStatus.CANCELLED) {
                throw new BusinessException("Cannot bill cancelled order: " + o.getOrderNumber(), HttpStatus.BAD_REQUEST, "CANNOT_BILL_CANCELLED_ORDER");
            }

            // Check if order already belongs to an active (non-voided) bill
            Optional<RestaurantBill> activeExistingBill = billRepository.findActiveBillByOrderIdAndTenantId(o.getId(), tenantId);
            if (activeExistingBill.isPresent()) {
                throw new BusinessException("Order " + o.getOrderNumber() + " is already billed under bill " + activeExistingBill.get().getBillNumber(),
                        HttpStatus.CONFLICT, "ORDER_ALREADY_BILLED");
            }

            if (commonBranchId == null) {
                commonBranchId = o.getBranchId();
            }
            if (commonTableNumber == null) {
                commonTableNumber = o.getTableNumber();
            }
            if (commonCustomerSessionId == null) {
                commonCustomerSessionId = o.getCustomerSessionId();
            }
        }

        // 2. Resolve Restaurant Branding & Branch Address Snapshot
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant not found", HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND"));
        String restaurantName = tenant.getBusinessName();

        String branchName = null;
        String branchAddress = null;
        if (commonBranchId != null) {
            Optional<Branch> bOpt = branchRepository.findByIdAndTenantId(commonBranchId, tenantId);
            if (bOpt.isPresent()) {
                branchName = bOpt.get().getName();
                branchAddress = bOpt.get().getAddress();
            }
        }

        // 3. Resolve Financial Configuration (Currency, Tax, Service Charge) from HotelProfile
        Optional<HotelProfile> profileOpt = hotelProfileRepository.findByTenantId(tenantId);
        String currencyCode = profileOpt.map(HotelProfile::getCurrencyCode).filter(c -> !c.isBlank()).orElse("INR");
        BigDecimal taxPercentage = profileOpt.map(HotelProfile::getTaxPercentage).orElse(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal serviceChargePercentage = profileOpt.map(HotelProfile::getServiceChargePercentage).orElse(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        // 4. Calculate Subtotal from Order Item Snapshots
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (RestaurantOrder o : orders) {
            if (o.getItems() != null) {
                for (RestaurantOrderItem item : o.getItems()) {
                    subtotal = subtotal.add(item.getLineTotal());
                }
            }
        }

        // 5. Apply Discretionary Discounts (if specified)
        BigDecimal discountAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (request.discountAmount() != null && request.discountAmount().compareTo(BigDecimal.ZERO) > 0) {
            discountAmount = request.discountAmount().setScale(2, RoundingMode.HALF_UP);
            if (discountAmount.compareTo(subtotal) > 0) {
                throw new BusinessException("Discount amount cannot exceed items subtotal", HttpStatus.BAD_REQUEST, "DISCOUNT_EXCEEDS_SUBTOTAL");
            }
        }

        BigDecimal netTaxableSubtotal = subtotal.subtract(discountAmount).setScale(2, RoundingMode.HALF_UP);

        // 6. Calculate Service Charge: (Net Subtotal * serviceChargePercentage / 100)
        BigDecimal serviceChargeAmount = netTaxableSubtotal
                .multiply(serviceChargePercentage)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        // 7. Calculate Tax: ((Net Subtotal + Service Charge) * taxPercentage / 100)
        BigDecimal taxableBase = netTaxableSubtotal.add(serviceChargeAmount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxAmount = taxableBase
                .multiply(taxPercentage)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        // 8. Grand Total & Outstanding Balance
        BigDecimal grandTotal = taxableBase.add(taxAmount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal paidAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal outstandingBalance = grandTotal;

        // 9. Instantiate RestaurantBill
        String billNumber = billNumberGenerator.generateUniqueBillNumber(tenantId);
        RestaurantBill bill = new RestaurantBill(tenantId, commonBranchId, commonCustomerSessionId, billNumber, commonTableNumber, restaurantName);
        bill.setBranchNameSnapshot(branchName);
        bill.setBranchAddressSnapshot(branchAddress);
        bill.setCurrencyCode(currencyCode);
        bill.setSubtotal(subtotal);
        bill.setDiscountAmount(discountAmount);
        bill.setTaxPercentage(taxPercentage);
        bill.setTaxAmount(taxAmount);
        bill.setServiceChargePercentage(serviceChargePercentage);
        bill.setServiceChargeAmount(serviceChargeAmount);
        bill.setGrandTotal(grandTotal);
        bill.setPaidAmount(paidAmount);
        bill.setOutstandingBalance(outstandingBalance);
        bill.setIssuedByUserId(principal.getUserId());
        bill.setStatus(BillStatus.ISSUED);
        bill.setIssuedAt(Instant.now());

        // 10. Clone Order Items and Options into Frozen Bill Snapshots
        for (RestaurantOrder o : orders) {
            bill.addOrderMapping(new BillOrderMapping(bill, o, o.getOrderNumber()));

            if (o.getItems() != null) {
                for (RestaurantOrderItem oi : o.getItems()) {
                    RestaurantBillItem bi = new RestaurantBillItem(
                            bill,
                            o,
                            oi.getId(),
                            oi.getItemNameSnapshot(),
                            oi.getVariantNameSnapshot(),
                            oi.getItemDescriptionSnapshot(),
                            oi.getQuantity(),
                            oi.getBaseUnitPrice(),
                            oi.getOptionsUnitPrice(),
                            oi.getEffectiveUnitPrice(),
                            oi.getLineTotal(),
                            oi.getSpecialInstructions()
                    );

                    if (oi.getOptions() != null) {
                        for (RestaurantOrderItemOption opt : oi.getOptions()) {
                            RestaurantBillItemOption bio = new RestaurantBillItemOption(
                                    bi,
                                    opt.getOptionId(),
                                    opt.getOptionNameSnapshot(),
                                    opt.getPriceContribution()
                            );
                            bi.addOption(bio);
                        }
                    }

                    bill.addItem(bi);
                }
            }
        }

        // 11. Record Initial Audit History
        RestaurantBillStatusHistory history = new RestaurantBillStatusHistory(
                bill,
                null,
                BillStatus.ISSUED.name(),
                principal.getUserId(),
                principal.getRoleCode() != null ? principal.getRoleCode() : "STAFF",
                principal.getType() != null ? principal.getType() : "STAFF",
                request.specialNote() != null && !request.specialNote().isBlank() ? request.specialNote().trim() : "Bill generated for order(s)"
        );
        bill.addStatusHistory(history);

        // 12. Save & Flush
        RestaurantBill savedBill = billRepository.save(bill);
        log.info("Restaurant bill generated: Tenant: {}, BillNumber: {}, Orders: {}, GrandTotal: {} {}",
                tenantId, savedBill.getBillNumber(), request.orderIds().size(), savedBill.getCurrencyCode(), savedBill.getGrandTotal());

        return toBillResponse(savedBill);
    }

    /**
     * Voids an active unpaid restaurant bill with mandatory reason.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BillResponse voidBill(DineFlowPrincipal principal, UUID billId, VoidBillRequest request) {
        UUID tenantId = principal.getTenantId();
        RestaurantBill bill = billRepository.findByIdAndTenantId(billId, tenantId)
                .orElseThrow(() -> new BusinessException("Bill not found", HttpStatus.NOT_FOUND, "BILL_NOT_FOUND"));

        if (principal.getBranchId() != null && bill.getBranchId() != null && !bill.getBranchId().equals(principal.getBranchId())) {
            throw new BusinessException("Unauthorized cross-branch bill access", HttpStatus.FORBIDDEN, "CROSS_BRANCH_ACCESS");
        }
        if ("GUEST".equalsIgnoreCase(principal.getType())
                && (bill.getCustomerSessionId() == null || !bill.getCustomerSessionId().equals(principal.getUserId()))) {
            throw new BusinessException("Unauthorized cross-guest bill access", HttpStatus.FORBIDDEN, "UNAUTHORIZED_BILL_ACCESS");
        }

        if (bill.getStatus() == BillStatus.VOIDED) {
            throw new BusinessException("Bill is already voided", HttpStatus.BAD_REQUEST, "BILL_ALREADY_VOIDED");
        }

        if (bill.getStatus() == BillStatus.PAID) {
            throw new BusinessException("A paid bill cannot be voided. Issue a formal refund instead.", HttpStatus.CONFLICT, "CANNOT_VOID_PAID_BILL");
        }

        if (bill.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException("Cannot void bill with partial payments recorded.", HttpStatus.CONFLICT, "PARTIAL_PAYMENTS_EXIST");
        }

        String prevStatus = bill.getStatus().name();
        bill.setStatus(BillStatus.VOIDED);
        bill.setVoidReason(request.reason().trim());
        bill.setVoidedByUserId(principal.getUserId());
        bill.setVoidedAt(Instant.now());

        RestaurantBillStatusHistory history = new RestaurantBillStatusHistory(
                bill,
                prevStatus,
                BillStatus.VOIDED.name(),
                principal.getUserId(),
                principal.getRoleCode() != null ? principal.getRoleCode() : "STAFF",
                principal.getType() != null ? principal.getType() : "STAFF",
                request.reason().trim()
        );
        bill.addStatusHistory(history);

        RestaurantBill saved = billRepository.save(bill);
        log.info("Bill voided: BillNumber: {}, User: {}, Reason: {}", saved.getBillNumber(), principal.getUserId(), request.reason());
        return toBillResponse(saved);
    }

    /**
     * Lists bills for the authenticated staff tenant/branch with optional status & search filters.
     */
    @Transactional(readOnly = true)
    public List<BillResponse> listBills(DineFlowPrincipal principal, BillStatus status, String search, UUID requestedBranchId) {
        UUID tenantId = principal.getTenantId();
        UUID effectiveBranch = principal.getBranchId() != null ? principal.getBranchId() : requestedBranchId;
        if (principal.getBranchId() != null && requestedBranchId != null && !principal.getBranchId().equals(requestedBranchId)) {
            throw new BusinessException("Unauthorized cross-branch bill access", HttpStatus.FORBIDDEN, "CROSS_BRANCH_ACCESS");
        }

        List<RestaurantBill> bills = (status != null)
                ? (effectiveBranch != null
                ? billRepository.findAllByTenantIdAndBranchIdAndStatusOrderByCreatedAtDesc(tenantId, effectiveBranch, status)
                : billRepository.findAllByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status))
                : (effectiveBranch != null
                ? billRepository.findAllByTenantIdAndBranchIdOrderByIssuedAtDesc(tenantId, effectiveBranch)
                : billRepository.findAllByTenantIdOrderByIssuedAtDesc(tenantId));

        String q = search != null ? search.trim().toLowerCase() : null;
        return bills.stream()
                .filter(b -> q == null || q.isEmpty()
                        || b.getBillNumber().toLowerCase().contains(q)
                        || b.getTableNumber().toLowerCase().contains(q)
                        || b.getOrderMappings().stream().anyMatch(m -> m.getOrderNumber().toLowerCase().contains(q)))
                .map(this::toBillResponse)
                .toList();
    }

    /**
     * Retrieves bill details by ID.
     */
    @Transactional(readOnly = true)
    public BillResponse getBill(DineFlowPrincipal principal, UUID billId) {
        UUID tenantId = principal.getTenantId();
        RestaurantBill bill = billRepository.findByIdAndTenantId(billId, tenantId)
                .orElseThrow(() -> new BusinessException("Bill not found", HttpStatus.NOT_FOUND, "BILL_NOT_FOUND"));

        if (principal.getBranchId() != null && bill.getBranchId() != null && !bill.getBranchId().equals(principal.getBranchId())) {
            throw new BusinessException("Unauthorized cross-branch bill access", HttpStatus.FORBIDDEN, "CROSS_BRANCH_ACCESS");
        }
        if ("GUEST".equalsIgnoreCase(principal.getType())
                && (bill.getCustomerSessionId() == null || !bill.getCustomerSessionId().equals(principal.getUserId()))) {
            throw new BusinessException("Unauthorized cross-guest bill access", HttpStatus.FORBIDDEN, "UNAUTHORIZED_BILL_ACCESS");
        }

        return toBillResponse(bill);
    }

    /**
     * Retrieves active bill associated with an order ID.
     */
    @Transactional(readOnly = true)
    public BillResponse getBillByOrderId(DineFlowPrincipal principal, UUID orderId) {
        UUID tenantId = principal.getTenantId();
        RestaurantBill bill = billRepository.findActiveBillByOrderIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> new BusinessException("No active bill found for order", HttpStatus.NOT_FOUND, "BILL_NOT_FOUND_FOR_ORDER"));

        if (principal.getBranchId() != null && bill.getBranchId() != null && !bill.getBranchId().equals(principal.getBranchId())) {
            throw new BusinessException("Unauthorized cross-branch bill access", HttpStatus.FORBIDDEN, "CROSS_BRANCH_ACCESS");
        }
        if ("GUEST".equalsIgnoreCase(principal.getType())
                && (bill.getCustomerSessionId() == null || !bill.getCustomerSessionId().equals(principal.getUserId()))) {
            throw new BusinessException("Unauthorized cross-guest bill access", HttpStatus.FORBIDDEN, "UNAUTHORIZED_BILL_ACCESS");
        }

        return toBillResponse(bill);
    }

    /**
     * Resolves the current active bill for a guest customer session.
     */
    @Transactional(readOnly = true)
    public BillResponse getCustomerCurrentBill(DineFlowPrincipal principal) {
        UUID sessionId = principal.getUserId();
        RestaurantBill bill = billRepository.findFirstByCustomerSessionIdAndStatusInOrderByCreatedAtDesc(
                sessionId, List.of(BillStatus.ISSUED, BillStatus.PARTIALLY_PAID, BillStatus.PAID))
                .orElseThrow(() -> new BusinessException("No bill found for active session", HttpStatus.NOT_FOUND, "NO_ACTIVE_BILL"));

        return toBillResponse(bill);
    }

    public BillResponse toBillResponse(RestaurantBill bill) {
        List<String> orderNumbers = bill.getOrderMappings() != null
                ? bill.getOrderMappings().stream().map(BillOrderMapping::getOrderNumber).toList()
                : List.of();

        List<BillResponse.BillItemResponse> itemResponses = new ArrayList<>();
        if (bill.getItems() != null) {
            for (RestaurantBillItem item : bill.getItems()) {
                List<BillResponse.BillOptionResponse> optionResponses = new ArrayList<>();
                if (item.getOptions() != null) {
                    for (RestaurantBillItemOption opt : item.getOptions()) {
                        optionResponses.add(new BillResponse.BillOptionResponse(
                                opt.getId(),
                                opt.getOptionNameSnapshot(),
                                opt.getPriceContribution()
                        ));
                    }
                }

                itemResponses.add(new BillResponse.BillItemResponse(
                        item.getId(),
                        item.getOrder().getId(),
                        item.getItemNameSnapshot(),
                        item.getVariantNameSnapshot(),
                        item.getItemDescriptionSnapshot(),
                        item.getQuantity(),
                        item.getBaseUnitPrice(),
                        item.getOptionsUnitPrice(),
                        item.getEffectiveUnitPrice(),
                        item.getLineTotal(),
                        item.getSpecialInstructions(),
                        optionResponses
                ));
            }
        }

        List<BillResponse.BillStatusHistoryResponse> historyResponses = new ArrayList<>();
        if (bill.getStatusHistory() != null) {
            for (RestaurantBillStatusHistory h : bill.getStatusHistory()) {
                historyResponses.add(new BillResponse.BillStatusHistoryResponse(
                        h.getId(),
                        h.getPreviousStatus(),
                        h.getNewStatus(),
                        h.getChangedByUserId(),
                        h.getChangedByRole(),
                        h.getActorType(),
                        h.getReason(),
                        h.getCreatedAt()
                ));
            }
        }

        return new BillResponse(
                bill.getId(),
                bill.getTenantId(),
                bill.getBranchId(),
                bill.getCustomerSessionId(),
                bill.getBillNumber(),
                bill.getTableNumber(),
                bill.getStatus().name(),
                bill.getCurrencyCode(),
                bill.getRestaurantNameSnapshot(),
                bill.getBranchNameSnapshot(),
                bill.getBranchAddressSnapshot(),
                bill.getSubtotal(),
                bill.getDiscountAmount(),
                bill.getTaxPercentage(),
                bill.getTaxAmount(),
                bill.getServiceChargePercentage(),
                bill.getServiceChargeAmount(),
                bill.getGrandTotal(),
                bill.getPaidAmount(),
                bill.getOutstandingBalance(),
                bill.getVoidReason(),
                bill.getIssuedAt(),
                bill.getPaidAt(),
                bill.getVoidedAt(),
                orderNumbers,
                itemResponses,
                historyResponses
        );
    }
}
