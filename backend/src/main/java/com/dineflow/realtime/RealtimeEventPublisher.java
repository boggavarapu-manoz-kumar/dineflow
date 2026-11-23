package com.dineflow.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Transactional Outbox event recorder & post-commit relay trigger.
 * Persists every published {@link RealtimeEvent} inside the active database transaction so:
 *  - If the business transaction rolls back, the event is NEVER persisted or delivered.
 *  - If the business transaction commits, the event is durably saved and relayed immediately
 *    after commit (with ShedLock outbox sweep guaranteeing recovery if the process crashes).
 */
@Component
public class RealtimeEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RealtimeEventPublisher.class);

    private final RealtimeOutboxEventRepository outboxRepository;
    private final OutboxRelayService relayService;
    private final ObjectMapper objectMapper;

    public RealtimeEventPublisher(RealtimeOutboxEventRepository outboxRepository,
                                  OutboxRelayService relayService) {
        this.outboxRepository = outboxRepository;
        this.relayService = relayService;
        this.objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }

    @EventListener
    public void onRealtimeEvent(RealtimeEvent event) {
        if (event == null || event.tenantId() == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(event);
            RealtimeOutboxEvent saved = outboxRepository.save(new RealtimeOutboxEvent(event, json));
            final UUID outboxId = saved.getId();

            if (TransactionSynchronizationManager.isSynchronizationActive()
                    && TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        relayService.relayById(outboxId);
                    }
                });
            } else {
                relayService.relayById(outboxId);
            }
        } catch (Exception ex) {
            log.error("Failed to record realtime outbox event type={} resource={}: {}",
                    event.eventType(), event.resourceId(), ex.getMessage());
        }
    }
}
