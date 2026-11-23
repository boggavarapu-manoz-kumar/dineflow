package com.dineflow.payment.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Bounded exponential-backoff retry policy with jitter for payment and refund reconciliation.
 * Separates transient provider outages from exhausted attempts requiring manual operator review.
 */
public final class ReconciliationRetryPolicy {

    public static final int DEFAULT_MAX_RETRIES = 5;
    private static final long BASE_DELAY_SECONDS = 60L;
    private static final long MAX_DELAY_SECONDS = 1800L; // 30 minutes cap

    private ReconciliationRetryPolicy() {
    }

    public static boolean isExhausted(int nextRetryCount) {
        return nextRetryCount >= DEFAULT_MAX_RETRIES;
    }

    public static Instant computeNextAttemptAt(Instant now, int nextRetryCount) {
        int exponent = Math.max(0, Math.min(nextRetryCount - 1, 10));
        long rawSeconds = Math.min(MAX_DELAY_SECONDS, BASE_DELAY_SECONDS * (1L << exponent));
        // +/- 15% jitter to prevent synchronized thundering-herd retries across payments
        long jitterRange = Math.max(1L, (long) (rawSeconds * 0.15));
        long jitter = ThreadLocalRandom.current().nextLong(-jitterRange, jitterRange + 1);
        long finalSeconds = Math.max(15L, rawSeconds + jitter);
        return now.plus(Duration.ofSeconds(finalSeconds));
    }
}
