package com.dineflow.promotion.service;

import com.dineflow.billing.domain.BillStatus;
import com.dineflow.billing.domain.RestaurantBill;
import com.dineflow.billing.domain.RestaurantBillItem;
import com.dineflow.billing.repository.RestaurantBillRepository;
import com.dineflow.common.exception.BusinessException;
import com.dineflow.menu.domain.MenuItem;
import com.dineflow.menu.repository.MenuItemRepository;
import com.dineflow.order.domain.RestaurantOrderItem;
import com.dineflow.promotion.domain.DiscountType;
import com.dineflow.promotion.domain.Promotion;
import com.dineflow.promotion.domain.PromotionRedemption;
import com.dineflow.promotion.domain.PromotionScope;
import com.dineflow.promotion.domain.RedemptionStatus;
import com.dineflow.promotion.dto.CouponApplicationResponse;
import com.dineflow.promotion.dto.CreatePromotionRequest;
import com.dineflow.promotion.dto.PromotionAnalyticsResponse;
import com.dineflow.promotion.dto.PromotionResponse;
import com.dineflow.promotion.dto.RedemptionResponse;
import com.dineflow.promotion.dto.UpdatePromotionRequest;
import com.dineflow.promotion.repository.PromotionRedemptionRepository;
import com.dineflow.promotion.repository.PromotionRepository;
import com.dineflow.security.DineFlowPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PromotionEngineService {

    private static final Logger log = LoggerFactory.getLogger(PromotionEngineService.class);

    private final PromotionRepository promotionRepository;
    private final PromotionRedemptionRepository redemptionRepository;
    private final RestaurantBillRepository billRepository;
    private final MenuItemRepository menuItemRepository;

    public PromotionEngineService(PromotionRepository promotionRepository,
                                  PromotionRedemptionRepository redemptionRepository,
                                  RestaurantBillRepository billRepository,
                                  MenuItemRepository menuItemRepository) {
        this.promotionRepository = promotionRepository;
        this.redemptionRepository = redemptionRepository;
        this.billRepository = billRepository;
        this.menuItemRepository = menuItemRepository;
    }

    // =========================================================================
    // ADMIN MANAGEMENT OPERATIONS
    // =========================================================================

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PromotionResponse createPromotion(DineFlowPrincipal principal, CreatePromotionRequest request) {
        UUID tenantId = principal.getTenantId();
        validatePromotionDatesAndDiscount(request.discountType(), request.discountValue(), request.startsAt(), request.endsAt());

        String normalizedCode = normalizeCouponCode(request.couponCode());
        if (normalizedCode != null && promotionRepository.existsByTenantIdAndCouponCodeIgnoreCase(tenantId, normalizedCode)) {
            throw new BusinessException("Coupon code already exists for this tenant", HttpStatus.CONFLICT, "DUPLICATE_COUPON_CODE");
        }

        UUID effectiveBranchId = request.branchId();
        if (principal.getBranchId() != null) {
            if (effectiveBranchId != null && !effectiveBranchId.equals(principal.getBranchId())) {
                throw new BusinessException("Branch managers can only create promotions for their assigned branch",
                        HttpStatus.FORBIDDEN, "CROSS_BRANCH_ACCESS");
            }
            effectiveBranchId = principal.getBranchId();
        }

        Promotion promotion = new Promotion();
        promotion.setTenantId(tenantId);
        promotion.setBranchId(effectiveBranchId);
        promotion.setName(request.name().trim());
        promotion.setDescription(request.description() != null ? request.description().trim() : null);
        promotion.setCouponCode(normalizedCode);
        promotion.setDiscountType(request.discountType());
        promotion.setDiscountValue(request.discountValue().setScale(2, RoundingMode.HALF_UP));
        promotion.setScope(request.scope() != null ? request.scope() : PromotionScope.ORDER_LEVEL);
        promotion.setMinOrderValue(request.minOrderValue() != null
                ? request.minOrderValue().max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        promotion.setMaxDiscountAmount(request.maxDiscountAmount() != null
                ? request.maxDiscountAmount().setScale(2, RoundingMode.HALF_UP)
                : null);
        promotion.setStartsAt(request.startsAt());
        promotion.setEndsAt(request.endsAt());
        promotion.setUsageLimit(request.usageLimit());
        promotion.setPerCustomerLimit(request.perCustomerLimit());
        promotion.setApplyBeforeTax(request.applyBeforeTax() == null || request.applyBeforeTax());
        promotion.setCombinable(request.combinable() != null && request.combinable());
        promotion.setActive(request.active() == null || request.active());
        promotion.setCreatedByUserId(principal.getUserId());
        promotion.setEligibleItemIds(request.eligibleItemIds());
        promotion.setEligibleCategoryIds(request.eligibleCategoryIds());

        Promotion saved = promotionRepository.save(promotion);
        log.info("Promotion created: id={}, name={}, code={}, tenant={}",
                saved.getId(), saved.getName(), saved.getCouponCode(), tenantId);
        return toPromotionResponse(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PromotionResponse updatePromotion(DineFlowPrincipal principal, UUID promotionId, UpdatePromotionRequest request) {
        UUID tenantId = principal.getTenantId();
        Promotion promotion = promotionRepository.findByIdAndTenantId(promotionId, tenantId)
                .orElseThrow(() -> new BusinessException("Promotion not found", HttpStatus.NOT_FOUND, "PROMOTION_NOT_FOUND"));

        enforceBranchScope(principal, promotion.getBranchId());

        DiscountType effectiveType = request.discountType() != null ? request.discountType() : promotion.getDiscountType();
        BigDecimal effectiveValue = request.discountValue() != null ? request.discountValue() : promotion.getDiscountValue();
        Instant effectiveStart = request.startsAt() != null ? request.startsAt() : promotion.getStartsAt();
        Instant effectiveEnd = request.endsAt() != null ? request.endsAt() : promotion.getEndsAt();
        validatePromotionDatesAndDiscount(effectiveType, effectiveValue, effectiveStart, effectiveEnd);

        if (request.name() != null && !request.name().isBlank()) promotion.setName(request.name().trim());
        if (request.description() != null) promotion.setDescription(request.description().trim());
        if (request.branchId() != null && principal.getBranchId() == null) promotion.setBranchId(request.branchId());
        if (request.discountType() != null) promotion.setDiscountType(request.discountType());
        if (request.discountValue() != null) promotion.setDiscountValue(request.discountValue().setScale(2, RoundingMode.HALF_UP));
        if (request.scope() != null) promotion.setScope(request.scope());
        if (request.minOrderValue() != null) promotion.setMinOrderValue(request.minOrderValue().max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
        if (request.maxDiscountAmount() != null) promotion.setMaxDiscountAmount(request.maxDiscountAmount().setScale(2, RoundingMode.HALF_UP));
        if (request.startsAt() != null) promotion.setStartsAt(request.startsAt());
        if (request.endsAt() != null) promotion.setEndsAt(request.endsAt());
        if (request.usageLimit() != null) promotion.setUsageLimit(request.usageLimit());
        if (request.perCustomerLimit() != null) promotion.setPerCustomerLimit(request.perCustomerLimit());
        if (request.applyBeforeTax() != null) promotion.setApplyBeforeTax(request.applyBeforeTax());
        if (request.combinable() != null) promotion.setCombinable(request.combinable());
        if (request.active() != null) promotion.setActive(request.active());
        if (request.eligibleItemIds() != null) promotion.setEligibleItemIds(request.eligibleItemIds());
        if (request.eligibleCategoryIds() != null) promotion.setEligibleCategoryIds(request.eligibleCategoryIds());

        Promotion saved = promotionRepository.save(promotion);
        return toPromotionResponse(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PromotionResponse setPromotionActive(DineFlowPrincipal principal, UUID promotionId, boolean active) {
        UUID tenantId = principal.getTenantId();
        Promotion promotion = promotionRepository.findByIdAndTenantId(promotionId, tenantId)
                .orElseThrow(() -> new BusinessException("Promotion not found", HttpStatus.NOT_FOUND, "PROMOTION_NOT_FOUND"));
        enforceBranchScope(principal, promotion.getBranchId());

        promotion.setActive(active);
        return toPromotionResponse(promotionRepository.save(promotion));
    }

    @Transactional(readOnly = true)
    public List<PromotionResponse> listPromotions(DineFlowPrincipal principal) {
        UUID tenantId = principal.getTenantId();
        return promotionRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(p -> principal.getBranchId() == null || p.getBranchId() == null || p.getBranchId().equals(principal.getBranchId()))
                .map(this::toPromotionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RedemptionResponse> getPromotionRedemptions(DineFlowPrincipal principal, UUID promotionId) {
        UUID tenantId = principal.getTenantId();
        Promotion promotion = promotionRepository.findByIdAndTenantId(promotionId, tenantId)
                .orElseThrow(() -> new BusinessException("Promotion not found", HttpStatus.NOT_FOUND, "PROMOTION_NOT_FOUND"));
        enforceBranchScope(principal, promotion.getBranchId());

        return redemptionRepository.findAllByPromotionIdAndTenantIdOrderByRedeemedAtDesc(promotionId, tenantId).stream()
                .map(this::toRedemptionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PromotionAnalyticsResponse getAnalytics(DineFlowPrincipal principal) {
        UUID tenantId = principal.getTenantId();
        List<Promotion> promotions = promotionRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(p -> principal.getBranchId() == null || p.getBranchId() == null || p.getBranchId().equals(principal.getBranchId()))
                .toList();

        Instant now = Instant.now();
        long total = promotions.size();
        long expired = promotions.stream().filter(p -> p.getEndsAt().isBefore(now)).count();
        long active = promotions.stream().filter(p -> p.isActive() && !p.getEndsAt().isBefore(now) && !p.getStartsAt().isAfter(now)).count();

        List<PromotionRedemption> appliedRedemptions = redemptionRepository.findAllByTenantIdOrderByRedeemedAtDesc(tenantId).stream()
                .filter(r -> r.getStatus() == RedemptionStatus.APPLIED)
                .filter(r -> principal.getBranchId() == null || r.getBranchId() == null || r.getBranchId().equals(principal.getBranchId()))
                .toList();

        BigDecimal totalGranted = appliedRedemptions.stream()
                .map(PromotionRedemption::getDiscountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        String currency = appliedRedemptions.isEmpty() ? "INR" : appliedRedemptions.get(0).getCurrencyCode();

        return new PromotionAnalyticsResponse(total, active, expired, appliedRedemptions.size(), totalGranted, currency);
    }

    // =========================================================================
    // COUPON VALIDATION, APPLICATION & REMOVAL (AUTHORITATIVE SERVER CALCULATION)
    // =========================================================================

    /**
     * Dry-run validation of a coupon code against a specific bill.
     * Returns customer-safe preview without mutating database state.
     */
    @Transactional(readOnly = true)
    public CouponApplicationResponse validateCouponForBill(DineFlowPrincipal principal, UUID billId, String rawCouponCode) {
        UUID tenantId = principal.getTenantId();
        RestaurantBill bill = loadAndAuthorizeBill(principal, billId);

        String code = normalizeCouponCode(rawCouponCode);
        if (code == null) {
            return invalidPreview(bill, rawCouponCode, "Please enter a valid coupon code");
        }

        Optional<Promotion> promoOpt = promotionRepository.findByTenantIdAndCouponCodeIgnoreCase(tenantId, code);
        if (promoOpt.isEmpty()) {
            return invalidPreview(bill, code, "Invalid or ineligible coupon code");
        }

        Promotion promotion = promoOpt.get();
        try {
            validateEligibility(promotion, bill, Instant.now());
            BigDecimal discount = calculateDiscountAmount(promotion, bill);
            BillTotalsPreview preview = calculateBillTotals(bill, bill.getDiscountAmount().add(discount), promotion.isApplyBeforeTax());
            return new CouponApplicationResponse(
                    true,
                    "Coupon is valid",
                    bill.getId(),
                    promotion.getCouponCode(),
                    promotion.getName(),
                    bill.getSubtotal(),
                    discount,
                    preview.serviceCharge(),
                    preview.tax(),
                    preview.grandTotal(),
                    preview.outstandingBalance(),
                    bill.getCurrencyCode(),
                    promotion.isApplyBeforeTax()
            );
        } catch (BusinessException ex) {
            return invalidPreview(bill, code, ex.getMessage());
        }
    }

    /**
     * Applies a coupon code to an unpaid bill with atomic concurrency-safe usage enforcement.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CouponApplicationResponse applyCouponToBill(DineFlowPrincipal principal, UUID billId, String rawCouponCode) {
        UUID tenantId = principal.getTenantId();
        RestaurantBill bill = loadAndAuthorizeBill(principal, billId);

        if (bill.getStatus() == BillStatus.PAID || bill.getStatus() == BillStatus.VOIDED) {
            throw new BusinessException("Cannot apply coupon to a " + bill.getStatus() + " bill",
                    HttpStatus.CONFLICT, "BILL_NOT_MODIFIABLE");
        }
        if (bill.getPaidAmount() != null && bill.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException("Cannot apply coupon after partial or full payment has been recorded",
                    HttpStatus.CONFLICT, "BILL_ALREADY_HAS_PAYMENTS");
        }

        String code = normalizeCouponCode(rawCouponCode);
        if (code == null) {
            throw new BusinessException("Coupon code is required", HttpStatus.BAD_REQUEST, "COUPON_CODE_REQUIRED");
        }

        Promotion promotion = promotionRepository.findByTenantIdAndCouponCodeIgnoreCase(tenantId, code)
                .orElseThrow(() -> new BusinessException("Invalid or ineligible coupon code", HttpStatus.BAD_REQUEST, "INVALID_COUPON"));

        validateEligibility(promotion, bill, Instant.now());

        // Compute authoritative discount server-side
        BigDecimal discountAmount = calculateDiscountAmount(promotion, bill);
        if (discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Coupon does not yield a discount for this bill", HttpStatus.BAD_REQUEST, "ZERO_DISCOUNT");
        }

        // Atomic concurrency-safe usage increment (prevents two concurrent threads from exceeding usage_limit)
        int updatedRows = promotionRepository.incrementUsageIfAvailable(promotion.getId(), tenantId);
        if (updatedRows == 0) {
            throw new BusinessException("Coupon usage limit has been reached", HttpStatus.CONFLICT, "COUPON_USAGE_LIMIT_REACHED");
        }

        // Persist redemption audit record
        String actorType = "GUEST".equalsIgnoreCase(principal.getType()) ? "CUSTOMER" : "STAFF";
        PromotionRedemption redemption = new PromotionRedemption(
                tenantId,
                bill.getBranchId(),
                promotion,
                bill,
                bill.getCustomerSessionId(),
                discountAmount,
                bill.getCurrencyCode(),
                principal.getUserId(),
                actorType
        );
        redemptionRepository.save(redemption);

        // Recalculate authoritative bill totals
        BigDecimal totalDiscount = recalculateTotalActiveDiscount(bill.getId(), tenantId, bill.getSubtotal());
        applyRecalculatedTotalsToBill(bill, totalDiscount, promotion.isApplyBeforeTax());
        billRepository.save(bill);

        log.info("Coupon {} applied to bill {} (discount: {} {}, new grandTotal: {})",
                promotion.getCouponCode(), bill.getBillNumber(), discountAmount, bill.getCurrencyCode(), bill.getGrandTotal());

        return new CouponApplicationResponse(
                true,
                "Coupon applied successfully",
                bill.getId(),
                promotion.getCouponCode(),
                promotion.getName(),
                bill.getSubtotal(),
                bill.getDiscountAmount(),
                bill.getServiceChargeAmount(),
                bill.getTaxAmount(),
                bill.getGrandTotal(),
                bill.getOutstandingBalance(),
                bill.getCurrencyCode(),
                promotion.isApplyBeforeTax()
        );
    }

    /**
     * Removes an applied coupon from an unpaid bill and restores usage count and original bill totals.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CouponApplicationResponse removeCouponFromBill(DineFlowPrincipal principal, UUID billId, String rawCouponCode) {
        UUID tenantId = principal.getTenantId();
        RestaurantBill bill = loadAndAuthorizeBill(principal, billId);

        if (bill.getStatus() == BillStatus.PAID || bill.getStatus() == BillStatus.VOIDED) {
            throw new BusinessException("Cannot remove coupon from a " + bill.getStatus() + " bill",
                    HttpStatus.CONFLICT, "BILL_NOT_MODIFIABLE");
        }
        if (bill.getPaidAmount() != null && bill.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException("Cannot remove coupon after payments have been recorded",
                    HttpStatus.CONFLICT, "BILL_ALREADY_HAS_PAYMENTS");
        }

        List<PromotionRedemption> activeRedemptions = redemptionRepository
                .findAllByBillIdAndTenantIdAndStatus(billId, tenantId, RedemptionStatus.APPLIED);

        if (activeRedemptions.isEmpty()) {
            throw new BusinessException("No active coupon applied to this bill", HttpStatus.NOT_FOUND, "NO_ACTIVE_COUPON");
        }

        String normalizedCode = normalizeCouponCode(rawCouponCode);
        List<PromotionRedemption> toRemove = normalizedCode == null
                ? activeRedemptions
                : activeRedemptions.stream()
                .filter(r -> normalizedCode.equalsIgnoreCase(r.getCouponCodeSnapshot()))
                .toList();

        if (toRemove.isEmpty()) {
            throw new BusinessException("Specified coupon is not applied to this bill", HttpStatus.NOT_FOUND, "COUPON_NOT_APPLIED");
        }

        Instant now = Instant.now();
        for (PromotionRedemption redemption : toRemove) {
            redemption.setStatus(RedemptionStatus.REMOVED);
            redemption.setRemovedAt(now);
            redemptionRepository.save(redemption);
            promotionRepository.decrementUsageIfPositive(redemption.getPromotion().getId(), tenantId);
        }

        BigDecimal remainingDiscount = recalculateTotalActiveDiscount(billId, tenantId, bill.getSubtotal());
        applyRecalculatedTotalsToBill(bill, remainingDiscount, true);
        billRepository.save(bill);

        return new CouponApplicationResponse(
                true,
                "Coupon removed",
                bill.getId(),
                null,
                null,
                bill.getSubtotal(),
                bill.getDiscountAmount(),
                bill.getServiceChargeAmount(),
                bill.getTaxAmount(),
                bill.getGrandTotal(),
                bill.getOutstandingBalance(),
                bill.getCurrencyCode(),
                true
        );
    }

    // =========================================================================
    // INTERNAL VALIDATION & CALCULATION ENGINE
    // =========================================================================

    private void validateEligibility(Promotion promotion, RestaurantBill bill, Instant now) {
        if (!promotion.isActive()) {
            throw new BusinessException("Coupon is not active", HttpStatus.BAD_REQUEST, "COUPON_INACTIVE");
        }
        if (now.isBefore(promotion.getStartsAt())) {
            throw new BusinessException("Coupon is not yet active", HttpStatus.BAD_REQUEST, "COUPON_NOT_STARTED");
        }
        if (now.isAfter(promotion.getEndsAt())) {
            throw new BusinessException("Coupon has expired", HttpStatus.BAD_REQUEST, "COUPON_EXPIRED");
        }
        if (promotion.getBranchId() != null && (bill.getBranchId() == null || !promotion.getBranchId().equals(bill.getBranchId()))) {
            throw new BusinessException("Coupon is not valid at this branch", HttpStatus.BAD_REQUEST, "COUPON_BRANCH_MISMATCH");
        }
        if (promotion.getMinOrderValue() != null && bill.getSubtotal().compareTo(promotion.getMinOrderValue()) < 0) {
            throw new BusinessException(String.format("Minimum order value of %s %s is required",
                    bill.getCurrencyCode(), promotion.getMinOrderValue()), HttpStatus.BAD_REQUEST, "MIN_ORDER_VALUE_NOT_MET");
        }
        if (promotion.getUsageLimit() != null && promotion.getCurrentUsageCount() >= promotion.getUsageLimit()) {
            throw new BusinessException("Coupon usage limit has been reached", HttpStatus.CONFLICT, "COUPON_USAGE_LIMIT_REACHED");
        }

        // Duplicate redemption on same bill
        if (redemptionRepository.existsByBillIdAndPromotionIdAndTenantIdAndStatus(
                bill.getId(), promotion.getId(), bill.getTenantId(), RedemptionStatus.APPLIED)) {
            throw new BusinessException("Coupon is already applied to this bill", HttpStatus.CONFLICT, "DUPLICATE_REDEMPTION");
        }

        // Combinability check
        List<PromotionRedemption> activeOnBill = redemptionRepository
                .findAllByBillIdAndTenantIdAndStatus(bill.getId(), bill.getTenantId(), RedemptionStatus.APPLIED);
        if (!activeOnBill.isEmpty()) {
            boolean existingNonCombinable = activeOnBill.stream().anyMatch(r -> !r.getPromotion().isCombinable());
            if (!promotion.isCombinable() || existingNonCombinable) {
                throw new BusinessException("This coupon cannot be combined with another active discount",
                        HttpStatus.CONFLICT, "PROMOTION_NOT_COMBINABLE");
            }
        }

        // Per-customer / per-session usage limit
        if (promotion.getPerCustomerLimit() != null && bill.getCustomerSessionId() != null) {
            long customerUsed = redemptionRepository.countByPromotionIdAndTenantIdAndCustomerSessionIdAndStatus(
                    promotion.getId(), bill.getTenantId(), bill.getCustomerSessionId(), RedemptionStatus.APPLIED);
            if (customerUsed >= promotion.getPerCustomerLimit()) {
                throw new BusinessException("You have reached the usage limit for this coupon",
                        HttpStatus.CONFLICT, "PER_CUSTOMER_LIMIT_REACHED");
            }
        }
    }

    private BigDecimal calculateDiscountAmount(Promotion promotion, RestaurantBill bill) {
        BigDecimal eligibleBase = resolveEligibleBase(promotion, bill);
        if (eligibleBase.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("No eligible items on this bill qualify for this promotion",
                    HttpStatus.BAD_REQUEST, "NO_ELIGIBLE_ITEMS");
        }

        BigDecimal rawDiscount;
        if (promotion.getDiscountType() == DiscountType.FIXED_AMOUNT) {
            rawDiscount = promotion.getDiscountValue();
        } else {
            rawDiscount = eligibleBase
                    .multiply(promotion.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (promotion.getMaxDiscountAmount() != null && rawDiscount.compareTo(promotion.getMaxDiscountAmount()) > 0) {
                rawDiscount = promotion.getMaxDiscountAmount();
            }
        }

        // Discount can never exceed eligible base or bill subtotal
        BigDecimal cap = eligibleBase.min(bill.getSubtotal());
        if (rawDiscount.compareTo(cap) > 0) {
            rawDiscount = cap;
        }
        return rawDiscount.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveEligibleBase(Promotion promotion, RestaurantBill bill) {
        if (promotion.getScope() == PromotionScope.ORDER_LEVEL) {
            return bill.getSubtotal().setScale(2, RoundingMode.HALF_UP);
        }

        List<RestaurantBillItem> billItems = bill.getItems();
        if (billItems == null || billItems.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        // Build map of orderItemId -> menuItemId from the linked orders
        Map<UUID, UUID> orderItemToMenuItem = new HashMap<>();
        for (RestaurantBillItem bi : billItems) {
            if (bi.getOrder() != null && bi.getOrder().getItems() != null) {
                for (RestaurantOrderItem oi : bi.getOrder().getItems()) {
                    if (oi.getId() != null && oi.getMenuItemId() != null) {
                        orderItemToMenuItem.put(oi.getId(), oi.getMenuItemId());
                    }
                }
            }
        }

        if (promotion.getScope() == PromotionScope.ITEM_SPECIFIC) {
            Set<UUID> eligibleItems = promotion.getEligibleItemIds();
            BigDecimal sum = BigDecimal.ZERO;
            for (RestaurantBillItem bi : billItems) {
                UUID menuItemId = bi.getOrderItemId() != null ? orderItemToMenuItem.get(bi.getOrderItemId()) : null;
                if (menuItemId != null && eligibleItems.contains(menuItemId)) {
                    sum = sum.add(bi.getLineTotal());
                }
            }
            return sum.setScale(2, RoundingMode.HALF_UP);
        }

        if (promotion.getScope() == PromotionScope.CATEGORY_SPECIFIC) {
            Set<UUID> eligibleCategories = promotion.getEligibleCategoryIds();
            Set<UUID> menuItemIds = new HashSet<>(orderItemToMenuItem.values());
            Map<UUID, UUID> menuItemToCategory = menuItemRepository.findAllById(menuItemIds).stream()
                    .filter(m -> m.getCategory() != null)
                    .collect(Collectors.toMap(MenuItem::getId, m -> m.getCategory().getId()));

            BigDecimal sum = BigDecimal.ZERO;
            for (RestaurantBillItem bi : billItems) {
                UUID menuItemId = bi.getOrderItemId() != null ? orderItemToMenuItem.get(bi.getOrderItemId()) : null;
                UUID categoryId = menuItemId != null ? menuItemToCategory.get(menuItemId) : null;
                if (categoryId != null && eligibleCategories.contains(categoryId)) {
                    sum = sum.add(bi.getLineTotal());
                }
            }
            return sum.setScale(2, RoundingMode.HALF_UP);
        }

        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal recalculateTotalActiveDiscount(UUID billId, UUID tenantId, BigDecimal subtotal) {
        BigDecimal total = redemptionRepository
                .findAllByBillIdAndTenantIdAndStatus(billId, tenantId, RedemptionStatus.APPLIED)
                .stream()
                .map(PromotionRedemption::getDiscountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        return total.compareTo(subtotal) > 0 ? subtotal.setScale(2, RoundingMode.HALF_UP) : total;
    }

    private record BillTotalsPreview(BigDecimal serviceCharge, BigDecimal tax, BigDecimal grandTotal, BigDecimal outstandingBalance) {}

    private BillTotalsPreview calculateBillTotals(RestaurantBill bill, BigDecimal totalDiscount, boolean applyBeforeTax) {
        BigDecimal subtotal = bill.getSubtotal().setScale(2, RoundingMode.HALF_UP);
        BigDecimal cappedDiscount = totalDiscount.min(subtotal).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal scPct = bill.getServiceChargePercentage() != null ? bill.getServiceChargePercentage() : BigDecimal.ZERO;
        BigDecimal taxPct = bill.getTaxPercentage() != null ? bill.getTaxPercentage() : BigDecimal.ZERO;
        BigDecimal paid = bill.getPaidAmount() != null ? bill.getPaidAmount() : BigDecimal.ZERO;

        BigDecimal serviceCharge;
        BigDecimal tax;
        BigDecimal grandTotal;

        if (applyBeforeTax) {
            BigDecimal netSubtotal = subtotal.subtract(cappedDiscount).setScale(2, RoundingMode.HALF_UP);
            serviceCharge = netSubtotal.multiply(scPct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal taxableBase = netSubtotal.add(serviceCharge).setScale(2, RoundingMode.HALF_UP);
            tax = taxableBase.multiply(taxPct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            grandTotal = taxableBase.add(tax).setScale(2, RoundingMode.HALF_UP);
        } else {
            serviceCharge = subtotal.multiply(scPct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal taxableBase = subtotal.add(serviceCharge).setScale(2, RoundingMode.HALF_UP);
            tax = taxableBase.multiply(taxPct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            grandTotal = taxableBase.add(tax).subtract(cappedDiscount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal outstanding = grandTotal.subtract(paid).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        return new BillTotalsPreview(serviceCharge, tax, grandTotal, outstanding);
    }

    private void applyRecalculatedTotalsToBill(RestaurantBill bill, BigDecimal totalDiscount, boolean applyBeforeTax) {
        BillTotalsPreview preview = calculateBillTotals(bill, totalDiscount, applyBeforeTax);
        bill.setDiscountAmount(totalDiscount.min(bill.getSubtotal()).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
        bill.setServiceChargeAmount(preview.serviceCharge());
        bill.setTaxAmount(preview.tax());
        bill.setGrandTotal(preview.grandTotal());
        bill.setOutstandingBalance(preview.outstandingBalance());
    }

    private RestaurantBill loadAndAuthorizeBill(DineFlowPrincipal principal, UUID billId) {
        UUID tenantId = principal.getTenantId();
        RestaurantBill bill = billRepository.findByIdAndTenantId(billId, tenantId)
                .orElseThrow(() -> new BusinessException("Bill not found", HttpStatus.NOT_FOUND, "BILL_NOT_FOUND"));

        if (principal.getBranchId() != null && bill.getBranchId() != null && !bill.getBranchId().equals(principal.getBranchId())) {
            throw new BusinessException("Unauthorized cross-branch bill access", HttpStatus.FORBIDDEN, "CROSS_BRANCH_ACCESS");
        }
        if ("GUEST".equalsIgnoreCase(principal.getType())
                && bill.getCustomerSessionId() != null
                && !bill.getCustomerSessionId().equals(principal.getUserId())) {
            throw new BusinessException("Unauthorized bill access for this customer session", HttpStatus.FORBIDDEN, "UNAUTHORIZED_BILL_ACCESS");
        }
        return bill;
    }

    private void enforceBranchScope(DineFlowPrincipal principal, UUID resourceBranchId) {
        if (principal.getBranchId() != null && resourceBranchId != null && !resourceBranchId.equals(principal.getBranchId())) {
            throw new BusinessException("Unauthorized cross-branch promotion access", HttpStatus.FORBIDDEN, "CROSS_BRANCH_ACCESS");
        }
    }

    private void validatePromotionDatesAndDiscount(DiscountType type, BigDecimal value, Instant startsAt, Instant endsAt) {
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw new BusinessException("Promotion end timestamp must be after start timestamp", HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE");
        }
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Discount value must be greater than zero", HttpStatus.BAD_REQUEST, "INVALID_DISCOUNT_VALUE");
        }
        if (type == DiscountType.PERCENTAGE && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessException("Percentage discount cannot exceed 100%", HttpStatus.BAD_REQUEST, "PERCENTAGE_OUT_OF_RANGE");
        }
    }

    private String normalizeCouponCode(String code) {
        if (code == null || code.trim().isEmpty()) return null;
        return code.trim().toUpperCase();
    }

    private CouponApplicationResponse invalidPreview(RestaurantBill bill, String code, String message) {
        return new CouponApplicationResponse(
                false,
                message,
                bill.getId(),
                code,
                null,
                bill.getSubtotal(),
                bill.getDiscountAmount(),
                bill.getServiceChargeAmount(),
                bill.getTaxAmount(),
                bill.getGrandTotal(),
                bill.getOutstandingBalance(),
                bill.getCurrencyCode(),
                true
        );
    }

    private PromotionResponse toPromotionResponse(Promotion p) {
        return new PromotionResponse(
                p.getId(),
                p.getTenantId(),
                p.getBranchId(),
                p.getName(),
                p.getDescription(),
                p.getCouponCode(),
                p.getDiscountType().name(),
                p.getDiscountValue(),
                p.getScope().name(),
                p.getMinOrderValue(),
                p.getMaxDiscountAmount(),
                p.getStartsAt(),
                p.getEndsAt(),
                p.getUsageLimit(),
                p.getCurrentUsageCount(),
                p.getPerCustomerLimit(),
                p.isApplyBeforeTax(),
                p.isCombinable(),
                p.isActive(),
                p.getEndsAt().isBefore(Instant.now()),
                p.getEligibleItemIds(),
                p.getEligibleCategoryIds(),
                p.getCreatedAt()
        );
    }

    private RedemptionResponse toRedemptionResponse(PromotionRedemption r) {
        return new RedemptionResponse(
                r.getId(),
                r.getPromotion().getId(),
                r.getBill().getId(),
                r.getBill().getBillNumber(),
                r.getBranchId(),
                r.getCustomerSessionId(),
                r.getCouponCodeSnapshot(),
                r.getPromotionNameSnapshot(),
                r.getDiscountAmount(),
                r.getCurrencyCode(),
                r.getStatus().name(),
                r.getActorType(),
                r.getRedeemedAt(),
                r.getRemovedAt()
        );
    }
}
