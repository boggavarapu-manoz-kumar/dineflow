package com.dineflow.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Multi-replica event distributor.
 * In "redis" fan-out mode (staging/prod), publishes to Redis Pub/Sub channel so every replica
 * forwards the event to its locally connected WebSocket clients.
 * In "local" mode (dev/test), forwards directly to the local SimpMessagingTemplate.
 */
@Component
public class RealtimeEventDistributor {

    private static final Logger log = LoggerFactory.getLogger(RealtimeEventDistributor.class);
    public static final String REDIS_CHANNEL = "dineflow:realtime:events";

    public record FanoutEnvelope(String destination, RealtimeEvent event) {}

    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate redisTemplate;
    private final boolean redisFanoutEnabled;
    private final ObjectMapper objectMapper;

    @Autowired
    public RealtimeEventDistributor(SimpMessagingTemplate messagingTemplate,
                                    @Autowired(required = false) StringRedisTemplate redisTemplate,
                                    @Value("${dineflow.realtime.fanout:local}") String fanoutMode) {
        this.messagingTemplate = messagingTemplate;
        this.redisTemplate = redisTemplate;
        this.redisFanoutEnabled = "redis".equalsIgnoreCase(fanoutMode) && redisTemplate != null;
        this.objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }

    public void distribute(String destination, RealtimeEvent event) {
        if (redisFanoutEnabled) {
            try {
                String json = objectMapper.writeValueAsString(new FanoutEnvelope(destination, event));
                redisTemplate.convertAndSend(REDIS_CHANNEL, json);
                return;
            } catch (Exception ex) {
                log.error("Redis Pub/Sub fan-out failed for eventId={} destination={}: {}",
                        event.eventId(), destination, ex.getMessage());
                throw new RuntimeException("Redis realtime fan-out failed: " + ex.getMessage(), ex);
            }
        }
        deliverToLocalSubscribers(destination, event);
    }

    public void deliverToLocalSubscribers(String destination, RealtimeEvent event) {
        messagingTemplate.convertAndSend(destination, event);
    }

    public void onRedisPubSubMessage(String rawJson) {
        try {
            FanoutEnvelope envelope = objectMapper.readValue(rawJson, FanoutEnvelope.class);
            if (envelope != null && envelope.destination() != null && envelope.event() != null) {
                deliverToLocalSubscribers(envelope.destination(), envelope.event());
            }
        } catch (Exception ex) {
            log.warn("Failed to deserialize Redis Pub/Sub realtime envelope: {}", ex.getMessage());
        }
    }
}
