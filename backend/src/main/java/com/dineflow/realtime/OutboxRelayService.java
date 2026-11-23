package com.dineflow.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Asynchronous transactional outbox relay with exponential backoff, dead-lettering,
 * ShedLock-protected crash recovery sweep, and retention pruning.
 */
@Service
public class OutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);
    public static final int MAX_RETRIES = 5;

    private final RealtimeOutboxEventRepository outboxRepository;
    private final RealtimeEventDistributor distributor;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    @Autowired
    public OutboxRelayService(RealtimeOutboxEventRepository outboxRepository,
                              RealtimeEventDistributor distributor,
                              TransactionTemplate transactionTemplate,
                              @Autowired(required = false) MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;
        this.distributor = distributor;
        TransactionTemplate requiresNewTx = new TransactionTemplate(transactionTemplate.getTransactionManager());
        requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate = requiresNewTx;
        this.meterRegistry = meterRegistry;
        this.objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }

    public boolean relayById(UUID outboxId) {
        RealtimeOutboxEvent row = transactionTemplate.execute(s -> outboxRepository.findById(outboxId).orElse(null));
        if (row == null || !RealtimeOutboxEvent.STATUS_PENDING.equals(row.getStatus())) {
            return false;
        }
        return attemptDelivery(row);
    }

    @Scheduled(fixedDelayString = "${dineflow.realtime.outbox-relay-ms:15000}")
    @SchedulerLock(name = "realtimeOutboxRelayJob", lockAtLeastFor = "PT5S", lockAtMostFor = "PT2M")
    public void relayDueEvents() {
        List<RealtimeOutboxEvent> due = transactionTemplate.execute(s ->
                outboxRepository.findDueForPublication(Instant.now(), PageRequest.of(0, 50)).getContent());
        if (due == null || due.isEmpty()) {
            return;
        }
        for (RealtimeOutboxEvent row : due) {
            attemptDelivery(row);
        }
    }

    @Scheduled(cron = "${dineflow.realtime.outbox-prune-cron:0 15 3 * * *}")
    @SchedulerLock(name = "realtimeOutboxPruneJob", lockAtLeastFor = "PT30S", lockAtMostFor = "PT5M")
    public void pruneOldPublishedEvents() {
        Instant sevenDaysAgo = Instant.now().minus(Duration.ofDays(7));
        transactionTemplate.executeWithoutResult(s -> outboxRepository.deletePublishedBefore(sevenDaysAgo));
    }

    private boolean attemptDelivery(RealtimeOutboxEvent row) {
        try {
            RealtimeEvent event = objectMapper.readValue(row.getPayloadJson(), RealtimeEvent.class);
            // Network/broker/WebSocket delivery runs strictly OUTSIDE any DB transaction
            distributor.distribute(row.getDestination(), event);

            transactionTemplate.executeWithoutResult(s ->
                    outboxRepository.findById(row.getId()).ifPresent(fresh -> {
                        fresh.setStatus(RealtimeOutboxEvent.STATUS_PUBLISHED);
                        fresh.setPublishedAt(Instant.now());
                        fresh.setNextRetryAt(null);
                        fresh.setLastError(null);
                        outboxRepository.save(fresh);
                    }));
            recordMetric("published");
            return true;
        } catch (Exception ex) {
            log.warn("Outbox delivery failed for eventId={} attempt={}: {}",
                    row.getEventId(), row.getRetryCount() + 1, ex.getMessage());

            transactionTemplate.executeWithoutResult(s ->
                    outboxRepository.findById(row.getId()).ifPresent(fresh -> {
                        int nextRetry = fresh.getRetryCount() + 1;
                        fresh.setRetryCount(nextRetry);
                        String err = ex.getMessage();
                        fresh.setLastError(err != null && err.length() > 500 ? err.substring(0, 500) : err);
                        if (nextRetry >= MAX_RETRIES) {
                            fresh.setStatus(RealtimeOutboxEvent.STATUS_DEAD_LETTER);
                            fresh.setNextRetryAt(null);
                            recordMetric("dead_letter");
                        } else {
                            long backoffSeconds = Math.min(300L, 10L * (1L << (nextRetry - 1)));
                            fresh.setNextRetryAt(Instant.now().plusSeconds(backoffSeconds));
                            recordMetric("retry_scheduled");
                        }
                        outboxRepository.save(fresh);
                    }));
            return false;
        }
    }

    private void recordMetric(String outcome) {
        if (meterRegistry != null) {
            meterRegistry.counter("dineflow.realtime.outbox.events", "outcome", outcome).increment();
        }
    }
}
