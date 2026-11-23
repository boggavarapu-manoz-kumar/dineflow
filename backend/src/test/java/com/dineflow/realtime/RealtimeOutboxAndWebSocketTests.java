package com.dineflow.realtime;

import com.dineflow.order.domain.OrderStatus;
import com.dineflow.order.domain.RestaurantOrder;
import com.dineflow.order.repository.RestaurantOrderRepository;
import com.dineflow.security.DineFlowPrincipal;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class RealtimeOutboxAndWebSocketTests {

    @Autowired
    private WebSocketAuthChannelInterceptor authInterceptor;

    @Autowired
    private WebSocketConnectionTracker connectionTracker;

    @Autowired
    private RestaurantOrderRepository orderRepository;

    @Autowired
    private RealtimeOutboxEventRepository outboxRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Test
    void unauthorizedConnectionCrossTenantCrossBranchRoleAndGuestOwnershipAreEnforced() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID branch1 = UUID.randomUUID();
        UUID branch2 = UUID.randomUUID();
        UUID guestOwnerSession = UUID.randomUUID();
        UUID otherGuestSession = UUID.randomUUID();

        // 1. Missing Bearer token on STOMP CONNECT -> rejected
        StompHeaderAccessor connectNoAuth = StompHeaderAccessor.create(StompCommand.CONNECT);
        assertThrows(AccessDeniedException.class, () ->
                authInterceptor.preSend(MessageBuilder.createMessage(new byte[0], connectNoAuth.getMessageHeaders()), null));

        // Create an order owned by guestOwnerSession in tenantA / branch1
        RestaurantOrder order = new RestaurantOrder(tenantA, branch1, guestOwnerSession, "ORD-WS-" + UUID.randomUUID().toString().substring(0, 6), "T-4");
        order.setCurrencyCode("INR");
        order.setSubtotal(new BigDecimal("200.00"));
        order.setGrandTotal(new BigDecimal("200.00"));
        order.setStatus(OrderStatus.PLACED);
        order = orderRepository.save(order);

        // 2. Guest owning the order CAN subscribe to its order topic & own session topic
        DineFlowPrincipal ownerGuest = new DineFlowPrincipal(guestOwnerSession, tenantA, branch1, "GUEST", List.of("CUSTOMER_ORDER_VIEW_OWN"), "GUEST");
        authInterceptor.authorizeSubscription(subscribeAccessor(ownerGuest, "/topic/tenant/" + tenantA + "/orders/" + order.getId()));
        authInterceptor.authorizeSubscription(subscribeAccessor(ownerGuest, "/topic/tenant/" + tenantA + "/sessions/" + guestOwnerSession));

        // 3. Different guest in same tenant CANNOT subscribe to ownerGuest's order or session topic
        DineFlowPrincipal intruderGuest = new DineFlowPrincipal(otherGuestSession, tenantA, branch1, "GUEST", List.of("CUSTOMER_ORDER_VIEW_OWN"), "GUEST");
        final UUID orderId = order.getId();
        assertThrows(AccessDeniedException.class, () ->
                authInterceptor.authorizeSubscription(subscribeAccessor(intruderGuest, "/topic/tenant/" + tenantA + "/orders/" + orderId)));
        assertThrows(AccessDeniedException.class, () ->
                authInterceptor.authorizeSubscription(subscribeAccessor(intruderGuest, "/topic/tenant/" + tenantA + "/sessions/" + guestOwnerSession)));

        // 4. Guest CANNOT subscribe to staff kitchen/waiter/payments topics
        assertThrows(AccessDeniedException.class, () ->
                authInterceptor.authorizeSubscription(subscribeAccessor(ownerGuest, "/topic/tenant/" + tenantA + "/branch/" + branch1 + "/kitchen")));

        // 5. Kitchen staff CAN subscribe to kitchen, CANNOT subscribe to payments, CANNOT subscribe to branch2 or tenantB
        DineFlowPrincipal kitchenStaff = new DineFlowPrincipal(UUID.randomUUID(), tenantA, branch1, "KITCHEN_STAFF", List.of("KITCHEN_VIEW"), "STAFF");
        authInterceptor.authorizeSubscription(subscribeAccessor(kitchenStaff, "/topic/tenant/" + tenantA + "/branch/" + branch1 + "/kitchen"));
        assertThrows(AccessDeniedException.class, () ->
                authInterceptor.authorizeSubscription(subscribeAccessor(kitchenStaff, "/topic/tenant/" + tenantA + "/branch/" + branch1 + "/payments")));
        assertThrows(AccessDeniedException.class, () ->
                authInterceptor.authorizeSubscription(subscribeAccessor(kitchenStaff, "/topic/tenant/" + tenantA + "/branch/" + branch2 + "/kitchen")));
        assertThrows(AccessDeniedException.class, () ->
                authInterceptor.authorizeSubscription(subscribeAccessor(kitchenStaff, "/topic/tenant/" + tenantB + "/branch/" + branch1 + "/kitchen")));

        // 6. Cashier CAN subscribe to payments in own branch
        DineFlowPrincipal cashier = new DineFlowPrincipal(UUID.randomUUID(), tenantA, branch1, "CASHIER", List.of("PAYMENT_VIEW"), "STAFF");
        authInterceptor.authorizeSubscription(subscribeAccessor(cashier, "/topic/tenant/" + tenantA + "/branch/" + branch1 + "/payments"));
    }

    @Test
    void transactionalOutboxRollsBackOnBusinessFailureAndCommitsAndPublishesOnSuccess() {
        UUID tenantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        // 1. Rolled-back transaction MUST NOT persist any outbox event
        RealtimeEvent rolledBackEvent = RealtimeEvent.ofVersioned(
                "ORDER_PLACED", tenantId, branchId, "waiter", orderId, 1L, Map.of("orderId", orderId.toString()));
        assertThrows(RuntimeException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(rolledBackEvent);
            throw new RuntimeException("Simulated DB rollback after publishing event");
        }));
        assertTrue(outboxRepository.findByEventId(rolledBackEvent.eventId()).isEmpty(),
                "Outbox event MUST roll back atomically with failed business transaction");

        // 2. Committed transaction persists outbox event and relays it to PUBLISHED
        RealtimeEvent committedEvent = RealtimeEvent.ofVersioned(
                "ORDER_ACCEPTED", tenantId, branchId, "kitchen", orderId, 2L, Map.of("orderId", orderId.toString()));
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(committedEvent));

        RealtimeOutboxEvent persisted = outboxRepository.findByEventId(committedEvent.eventId()).orElseThrow();
        assertEquals(RealtimeOutboxEvent.STATUS_PUBLISHED, persisted.getStatus());
        assertNotNull(persisted.getPublishedAt());
        assertEquals(0, persisted.getRetryCount());
    }

    @Test
    void outboxRetriesTransientDeliveryFailureAndDeadLettersAfterMaxRetries() {
        UUID tenantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        RealtimeEvent event = RealtimeEvent.of("PAYMENT_SETTLED", tenantId, branchId, "payments", UUID.randomUUID(), Map.of("status", "SUCCEEDED"));

        RealtimeOutboxEvent saved = outboxRepository.save(new RealtimeOutboxEvent(
                event,
                "{\"eventId\":\"" + event.eventId() + "\",\"eventType\":\"PAYMENT_SETTLED\",\"eventVersion\":\"1.0\",\"tenantId\":\"" + tenantId + "\",\"branchId\":\"" + branchId + "\",\"channel\":\"payments\",\"payload\":{\"status\":\"SUCCEEDED\"}}"
        ));

        RealtimeEventDistributor failingDistributor = mock(RealtimeEventDistributor.class);
        doThrow(new RuntimeException("Simulated WebSocket broker disconnect"))
                .when(failingDistributor).distribute(anyString(), any(RealtimeEvent.class));

        OutboxRelayService isolatedRelay = new OutboxRelayService(
                outboxRepository, failingDistributor, transactionTemplate, new SimpleMeterRegistry());

        for (int attempt = 1; attempt <= OutboxRelayService.MAX_RETRIES; attempt++) {
            isolatedRelay.relayById(saved.getId());
        }

        RealtimeOutboxEvent deadLettered = outboxRepository.findById(saved.getId()).orElseThrow();
        assertEquals(RealtimeOutboxEvent.STATUS_DEAD_LETTER, deadLettered.getStatus());
        assertEquals(OutboxRelayService.MAX_RETRIES, deadLettered.getRetryCount());
        assertTrue(deadLettered.getLastError().contains("broker disconnect"));
    }

    @Test
    void multiReplicaRedisPubSubFanoutDeliversEventToRemoteReplicaSubscribers() {
        SimpMessagingTemplate replica1Template = mock(SimpMessagingTemplate.class);
        SimpMessagingTemplate replica2Template = mock(SimpMessagingTemplate.class);
        StringRedisTemplate mockRedis = mock(StringRedisTemplate.class);

        AtomicReference<String> publishedPubSubMessage = new AtomicReference<>();
        org.mockito.Mockito.doAnswer(inv -> {
            publishedPubSubMessage.set(inv.getArgument(1));
            return null;
        }).when(mockRedis).convertAndSend(eq(RealtimeEventDistributor.REDIS_CHANNEL), anyString());

        // Replica 1 publishes to Redis Pub/Sub; Replica 2 receives Pub/Sub message and delivers to its local WS clients
        RealtimeEventDistributor replica1 = new RealtimeEventDistributor(replica1Template, mockRedis, "redis");
        RealtimeEventDistributor replica2 = new RealtimeEventDistributor(replica2Template, mockRedis, "redis");

        UUID tenantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        RealtimeEvent event = RealtimeEvent.ofVersioned("ORDER_READY", tenantId, branchId, "waiter", UUID.randomUUID(), 3L, Map.of("table", "T-7"));
        String dest = event.resolveDestination();

        replica1.distribute(dest, event);
        assertNotNull(publishedPubSubMessage.get());

        // Simulate Redis delivering Pub/Sub message to Replica 2
        replica2.onRedisPubSubMessage(publishedPubSubMessage.get());
        verify(replica2Template, times(1)).convertAndSend(eq(dest), any(RealtimeEvent.class));
    }

    @Test
    void connectionTrackerEnforcesPerUserConnectionLimit() {
        UUID userId = UUID.randomUUID();
        for (int i = 0; i < WebSocketConnectionTracker.MAX_CONNECTIONS_PER_PRINCIPAL; i++) {
            connectionTracker.registerConnection(userId, "sess-" + userId + "-" + i);
        }
        assertEquals(WebSocketConnectionTracker.MAX_CONNECTIONS_PER_PRINCIPAL, connectionTracker.activeConnectionCount(userId));
        assertThrows(AccessDeniedException.class, () ->
                connectionTracker.registerConnection(userId, "sess-overflow-" + userId));
    }

    private StompHeaderAccessor subscribeAccessor(DineFlowPrincipal principal, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        accessor.setDestination(destination);
        return accessor;
    }
}
