package com.dineflow.realtime;

import com.dineflow.order.domain.RestaurantOrder;
import com.dineflow.order.repository.RestaurantOrderRepository;
import com.dineflow.security.DineFlowPrincipal;
import com.dineflow.security.JwtTokenService;
import io.jsonwebtoken.Claims;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Enforces JWT authentication on STOMP CONNECT, per-user connection limits,
 * and strict server-side multi-tenant / branch / RBAC / guest-ownership authorization
 * on every STOMP SUBSCRIBE frame.
 */
@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern BRANCH_TOPIC = Pattern.compile("^/topic/tenant/([0-9a-fA-F-]+)/branch/([0-9a-fA-F-]+)/(kitchen|waiter|payments)$");
    private static final Pattern ORDER_TOPIC = Pattern.compile("^/topic/tenant/([0-9a-fA-F-]+)/orders/([0-9a-fA-F-]+)$");
    private static final Pattern SESSION_TOPIC = Pattern.compile("^/topic/tenant/([0-9a-fA-F-]+)/sessions/([0-9a-fA-F-]+)$");

    private static final Set<String> ADMIN_ROLES = Set.of("SUPER_ADMIN", "HOTEL_OWNER", "HOTEL_ADMIN", "BRANCH_MANAGER");
    private static final Set<String> KITCHEN_PERMS = Set.of("KITCHEN_VIEW", "KITCHEN_UPDATE", "KITCHEN_MANAGE", "ORDER_VIEW", "ORDER_MANAGE");
    private static final Set<String> WAITER_PERMS = Set.of("WAITER_VIEW", "WAITER_MANAGE", "ORDER_VIEW", "ORDER_MANAGE", "ORDER_SERVE");
    private static final Set<String> PAYMENT_PERMS = Set.of("PAYMENT_VIEW", "PAYMENT_PROCESS", "BILL_VIEW", "BILL_COLLECT_PAYMENT", "REFUND_APPROVE", "REPORT_FINANCIAL_VIEW");

    private final JwtTokenService jwtTokenService;
    private final RestaurantOrderRepository orderRepository;
    private final WebSocketConnectionTracker connectionTracker;

    public WebSocketAuthChannelInterceptor(JwtTokenService jwtTokenService,
                                           RestaurantOrderRepository orderRepository,
                                           WebSocketConnectionTracker connectionTracker) {
        this.jwtTokenService = jwtTokenService;
        this.orderRepository = orderRepository;
        this.connectionTracker = connectionTracker;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new AccessDeniedException("Missing Bearer token on WebSocket CONNECT");
            }
            String token = authHeader.substring(7).trim();
            Optional<Claims> claimsOpt = jwtTokenService.validateAndParseClaims(token);
            if (claimsOpt.isEmpty()) {
                throw new AccessDeniedException("Invalid or expired JWT on WebSocket CONNECT");
            }
            Claims claims = claimsOpt.get();
            UUID userId = UUID.fromString(claims.getSubject());
            String tenantStr = claims.get("tenantId", String.class);
            UUID tenantId = tenantStr != null ? UUID.fromString(tenantStr) : null;
            String branchStr = claims.get("branchId", String.class);
            UUID branchId = branchStr != null ? UUID.fromString(branchStr) : null;
            String role = claims.get("role", String.class);
            String type = claims.get("type", String.class);
            @SuppressWarnings("unchecked")
            List<String> perms = (List<String>) claims.get("permissions", List.class);

            DineFlowPrincipal principal = new DineFlowPrincipal(userId, tenantId, branchId, role, perms != null ? perms : List.of(), type);
            connectionTracker.registerConnection(userId, accessor.getSessionId() != null ? accessor.getSessionId() : UUID.randomUUID().toString());
            accessor.setUser(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        }

        if (StompCommand.SEND.equals(accessor.getCommand())) {
            throw new AccessDeniedException("Client STOMP SEND frames are not permitted on server-pushed channels");
        }

        return message;
    }

    public void authorizeSubscription(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth)
                || !(auth.getPrincipal() instanceof DineFlowPrincipal principal)) {
            throw new AccessDeniedException("Unauthenticated WebSocket subscription rejected");
        }

        String dest = accessor.getDestination();
        if (dest == null || dest.isBlank()) {
            throw new AccessDeniedException("Missing subscription destination");
        }

        // 1. Staff branch-scoped operational topics: kitchen | waiter | payments
        Matcher branchMatcher = BRANCH_TOPIC.matcher(dest);
        if (branchMatcher.matches()) {
            UUID targetTenant = UUID.fromString(branchMatcher.group(1));
            UUID targetBranch = UUID.fromString(branchMatcher.group(2));
            String topicChannel = branchMatcher.group(3);

            if (principal.getTenantId() == null || !principal.getTenantId().equals(targetTenant)) {
                throw new AccessDeniedException("Cross-tenant WebSocket subscription forbidden");
            }
            if (!"STAFF".equalsIgnoreCase(principal.getType())) {
                throw new AccessDeniedException("Guest sessions cannot subscribe to staff operational topics");
            }
            if (principal.getBranchId() != null && !principal.getBranchId().equals(targetBranch)) {
                throw new AccessDeniedException("Cross-branch WebSocket subscription forbidden");
            }
            authorizeChannelRole(principal, topicChannel);
            return;
        }

        // 2. Order-specific topic: requires tenant match AND guest session ownership (or staff branch match)
        Matcher orderMatcher = ORDER_TOPIC.matcher(dest);
        if (orderMatcher.matches()) {
            UUID targetTenant = UUID.fromString(orderMatcher.group(1));
            UUID orderId = UUID.fromString(orderMatcher.group(2));
            if (principal.getTenantId() == null || !principal.getTenantId().equals(targetTenant)) {
                throw new AccessDeniedException("Cross-tenant order WebSocket subscription forbidden");
            }
            RestaurantOrder order = orderRepository.findByIdAndTenantId(orderId, targetTenant)
                    .orElseThrow(() -> new AccessDeniedException("Order not found or inaccessible for WebSocket subscription"));

            if ("GUEST".equalsIgnoreCase(principal.getType())) {
                if (order.getCustomerSessionId() == null || !order.getCustomerSessionId().equals(principal.getUserId())) {
                    throw new AccessDeniedException("Guest session does not own this order");
                }
            } else if ("STAFF".equalsIgnoreCase(principal.getType())) {
                if (principal.getBranchId() != null && order.getBranchId() != null
                        && !principal.getBranchId().equals(order.getBranchId())) {
                    throw new AccessDeniedException("Cross-branch order WebSocket subscription forbidden");
                }
            } else {
                throw new AccessDeniedException("Unsupported principal type for order WebSocket subscription");
            }
            return;
        }

        // 3. Guest-session private notification topic (payment/refund updates for own session)
        Matcher sessionMatcher = SESSION_TOPIC.matcher(dest);
        if (sessionMatcher.matches()) {
            UUID targetTenant = UUID.fromString(sessionMatcher.group(1));
            UUID targetSessionId = UUID.fromString(sessionMatcher.group(2));
            if (principal.getTenantId() == null || !principal.getTenantId().equals(targetTenant)) {
                throw new AccessDeniedException("Cross-tenant session WebSocket subscription forbidden");
            }
            if (!"GUEST".equalsIgnoreCase(principal.getType()) || !principal.getUserId().equals(targetSessionId)) {
                throw new AccessDeniedException("Session WebSocket topic is restricted to the owning guest session");
            }
            return;
        }

        throw new AccessDeniedException("Unsupported or unauthorized WebSocket topic: " + dest);
    }

    private void authorizeChannelRole(DineFlowPrincipal principal, String topicChannel) {
        String role = principal.getRoleCode() != null ? principal.getRoleCode().toUpperCase() : "";
        if (ADMIN_ROLES.contains(role)) {
            return;
        }
        Set<String> perms = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        boolean allowed = switch (topicChannel) {
            case "kitchen" -> "KITCHEN_STAFF".equals(role) || perms.stream().anyMatch(KITCHEN_PERMS::contains);
            case "waiter" -> "WAITER".equals(role) || perms.stream().anyMatch(WAITER_PERMS::contains);
            case "payments" -> "CASHIER".equals(role) || perms.stream().anyMatch(PAYMENT_PERMS::contains);
            default -> false;
        };
        if (!allowed) {
            throw new AccessDeniedException("Insufficient role/permissions for WebSocket channel: " + topicChannel);
        }
    }
}
