package com.dineflow.payment.service;

import com.dineflow.payment.domain.PaymentReconciliationLog;
import com.dineflow.payment.repository.PaymentReconciliationLogRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Provides tenant-scoped operational visibility into reconciliation job execution history
 * and ShedLock distributed lock state.
 */
@Service
public class ReconciliationOperationsService {

    public record JobExecutionView(
            UUID id,
            String reconciliationType,
            String executionStatus,
            String triggeredBy,
            int scannedCount,
            int reconciledCount,
            int mismatchCount,
            int retryCount,
            Long durationMs,
            Instant startedAt,
            Instant completedAt,
            String summary
    ) {}

    public record SchedulerLockStatus(
            String name,
            Instant lockUntil,
            Instant lockedAt,
            String lockedBy,
            boolean currentlyLocked
    ) {}

    private final PaymentReconciliationLogRepository logRepository;
    private final JdbcTemplate jdbcTemplate;

    public ReconciliationOperationsService(PaymentReconciliationLogRepository logRepository,
                                           JdbcTemplate jdbcTemplate) {
        this.logRepository = logRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<JobExecutionView> getRecentExecutions(UUID tenantId) {
        List<PaymentReconciliationLog> rows = tenantId != null
                ? logRepository.findTop20ByTenantIdOrderByStartedAtDesc(tenantId)
                : logRepository.findTop20ByOrderByStartedAtDesc();
        return rows.stream()
                .map(r -> new JobExecutionView(
                        r.getId(),
                        r.getReconciliationType(),
                        r.getExecutionStatus(),
                        r.getTriggeredBy(),
                        r.getScannedCount(),
                        r.getReconciledCount(),
                        r.getMismatchCount(),
                        r.getRetryCount(),
                        r.getDurationMs(),
                        r.getStartedAt(),
                        r.getCompletedAt(),
                        r.getDiscrepancyDetails()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SchedulerLockStatus> getSchedulerLocks() {
        Instant now = Instant.now();
        return jdbcTemplate.query(
                "SELECT name, lock_until, locked_at, locked_by FROM shedlock ORDER BY name ASC",
                (rs, rowNum) -> {
                    Instant lockUntil = rs.getTimestamp("lock_until").toInstant();
                    Instant lockedAt = rs.getTimestamp("locked_at").toInstant();
                    return new SchedulerLockStatus(
                            rs.getString("name"),
                            lockUntil,
                            lockedAt,
                            rs.getString("locked_by"),
                            lockUntil.isAfter(now)
                    );
                }
        );
    }
}
