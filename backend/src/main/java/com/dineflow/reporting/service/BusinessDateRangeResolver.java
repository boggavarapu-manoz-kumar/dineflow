package com.dineflow.reporting.service;

import com.dineflow.common.exception.BusinessException;
import com.dineflow.reporting.dto.ReportPeriodType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * Resolves tenant/branch local business dates and cross-midnight service cutoffs
 * into exact half-open UTC intervals [startUtc, endUtc).
 *
 * Example: With timezone=Asia/Kolkata and businessDayCutoffHour=4,
 * business day 2026-11-23 runs from 2026-11-23 04:00:00+05:30 to 2026-11-24 04:00:00+05:30.
 * A bill issued at 01:30 AM on 2026-11-24 belongs to business day 2026-11-23.
 */
@Component
public class BusinessDateRangeResolver {

    public static final String DEFAULT_TIMEZONE = "Asia/Kolkata";

    public record ResolvedDateRange(
            ReportPeriodType periodType,
            LocalDate fromDate,
            LocalDate toDate,
            ZoneId zoneId,
            int businessDayCutoffHour,
            Instant startUtc,
            Instant endUtc
    ) {}

    public ResolvedDateRange resolve(ReportPeriodType periodType,
                                     LocalDate fromDate,
                                     LocalDate toDate,
                                     String timezoneStr,
                                     Integer cutoffHourObj) {
        ZoneId zoneId = resolveZone(timezoneStr);
        int cutoffHour = cutoffHourObj != null ? cutoffHourObj : 0;
        if (cutoffHour < 0 || cutoffHour > 12) {
            throw new BusinessException("businessDayCutoffHour must be between 0 and 12",
                    HttpStatus.BAD_REQUEST, "INVALID_CUTOFF_HOUR");
        }

        ReportPeriodType effectiveType = periodType != null ? periodType : ReportPeriodType.DAILY;
        LocalDate todayBusiness = currentBusinessDate(zoneId, cutoffHour);

        LocalDate startLocal;
        LocalDate endLocal;

        switch (effectiveType) {
            case DAILY -> {
                startLocal = fromDate != null ? fromDate : todayBusiness;
                endLocal = startLocal;
            }
            case WEEKLY -> {
                LocalDate anchor = fromDate != null ? fromDate : todayBusiness;
                startLocal = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                endLocal = startLocal.plusDays(6);
            }
            case MONTHLY -> {
                LocalDate anchor = fromDate != null ? fromDate : todayBusiness;
                startLocal = anchor.withDayOfMonth(1);
                endLocal = anchor.withDayOfMonth(anchor.lengthOfMonth());
            }
            case CUSTOM -> {
                startLocal = fromDate != null ? fromDate : todayBusiness.minusDays(6);
                endLocal = toDate != null ? toDate : todayBusiness;
                if (endLocal.isBefore(startLocal)) {
                    throw new BusinessException("toDate cannot be before fromDate",
                            HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE");
                }
            }
            default -> {
                startLocal = todayBusiness;
                endLocal = todayBusiness;
            }
        }

        LocalTime cutoffTime = LocalTime.of(cutoffHour, 0);
        Instant startUtc = ZonedDateTime.of(startLocal, cutoffTime, zoneId).toInstant();
        Instant endUtc = ZonedDateTime.of(endLocal.plusDays(1), cutoffTime, zoneId).toInstant();

        return new ResolvedDateRange(effectiveType, startLocal, endLocal, zoneId, cutoffHour, startUtc, endUtc);
    }

    /**
     * Maps any UTC timestamp back to its local business date given timezone and cutoff hour.
     */
    public LocalDate toBusinessDate(Instant utcInstant, ZoneId zoneId, int cutoffHour) {
        ZonedDateTime local = utcInstant.atZone(zoneId);
        if (cutoffHour > 0 && local.getHour() < cutoffHour) {
            return local.toLocalDate().minusDays(1);
        }
        return local.toLocalDate();
    }

    private LocalDate currentBusinessDate(ZoneId zoneId, int cutoffHour) {
        return toBusinessDate(Instant.now(), zoneId, cutoffHour);
    }

    private ZoneId resolveZone(String timezoneStr) {
        if (timezoneStr == null || timezoneStr.isBlank()) {
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
        try {
            return ZoneId.of(timezoneStr.trim());
        } catch (Exception ex) {
            throw new BusinessException("Invalid IANA timezone: " + timezoneStr,
                    HttpStatus.BAD_REQUEST, "INVALID_TIMEZONE");
        }
    }
}
