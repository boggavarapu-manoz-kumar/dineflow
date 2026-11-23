package com.dineflow.realtime;

import com.dineflow.security.DineFlowPrincipal;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks active WebSocket sessions per principal to enforce per-user connection limits
 * and ensure automatic cleanup on disconnect.
 */
@Component
public class WebSocketConnectionTracker {

    public static final int MAX_CONNECTIONS_PER_PRINCIPAL = 10;

    private final Map<UUID, Set<String>> principalSessions = new ConcurrentHashMap<>();
    private final Map<String, UUID> sessionToPrincipal = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;

    @Autowired
    public WebSocketConnectionTracker(@Autowired(required = false) MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void registerConnection(UUID principalId, String sessionId) {
        if (principalId == null || sessionId == null) return;
        Set<String> sessions = principalSessions.computeIfAbsent(principalId, k -> ConcurrentHashMap.newKeySet());
        if (sessions.size() >= MAX_CONNECTIONS_PER_PRINCIPAL && !sessions.contains(sessionId)) {
            throw new AccessDeniedException("Maximum concurrent WebSocket connections exceeded for user");
        }
        sessions.add(sessionId);
        sessionToPrincipal.put(sessionId, principalId);
        if (meterRegistry != null) {
            meterRegistry.counter("dineflow.realtime.ws.connections", "event", "connect").increment();
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        UUID principalId = sessionToPrincipal.remove(sessionId);
        if (principalId == null && event.getUser() instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof DineFlowPrincipal p) {
            principalId = p.getUserId();
        }
        if (principalId != null) {
            Set<String> sessions = principalSessions.get(principalId);
            if (sessions != null) {
                sessions.remove(sessionId);
                if (sessions.isEmpty()) {
                    principalSessions.remove(principalId, sessions);
                }
            }
            if (meterRegistry != null) {
                meterRegistry.counter("dineflow.realtime.ws.connections", "event", "disconnect").increment();
            }
        }
    }

    public int activeConnectionCount(UUID principalId) {
        Set<String> sessions = principalSessions.get(principalId);
        return sessions != null ? sessions.size() : 0;
    }
}
