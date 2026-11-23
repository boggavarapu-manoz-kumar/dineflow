package com.dineflow.reporting.controller;

import com.dineflow.common.api.ApiResponse;
import com.dineflow.payment.domain.PaymentMethod;
import com.dineflow.reporting.dto.FinancialReportResponse;
import com.dineflow.reporting.dto.ReportPeriodType;
import com.dineflow.reporting.service.FinancialReportingService;
import com.dineflow.security.DineFlowPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports/financial")
public class FinancialReportingController {

    private final FinancialReportingService reportingService;

    public FinancialReportingController(FinancialReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('REPORT_FINANCIAL_VIEW') or hasAuthority('HOTEL_ADMIN') or hasAuthority('HOTEL_OWNER')")
    public ResponseEntity<ApiResponse<FinancialReportResponse>> getFinancialReport(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @RequestParam(value = "period", defaultValue = "DAILY") ReportPeriodType period,
            @RequestParam(value = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(value = "branchId", required = false) UUID branchId,
            @RequestParam(value = "timezone", required = false) String timezone,
            @RequestParam(value = "businessDayCutoffHour", required = false) Integer businessDayCutoffHour) {
        FinancialReportResponse report = reportingService.generateFinancialReport(
                principal, period, fromDate, toDate, branchId, timezone, businessDayCutoffHour);
        return ResponseEntity.ok(ApiResponse.ok(report, "Financial report generated"));
    }

    @GetMapping(value = "/export/csv", produces = "text/csv")
    @PreAuthorize("hasAuthority('REPORT_FINANCIAL_EXPORT') or hasAuthority('HOTEL_ADMIN') or hasAuthority('HOTEL_OWNER')")
    public ResponseEntity<StreamingResponseBody> exportCsv(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @RequestParam(value = "period", defaultValue = "DAILY") ReportPeriodType period,
            @RequestParam(value = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(value = "branchId", required = false) UUID branchId,
            @RequestParam(value = "paymentMethod", required = false) PaymentMethod paymentMethod,
            @RequestParam(value = "timezone", required = false) String timezone,
            @RequestParam(value = "businessDayCutoffHour", required = false) Integer businessDayCutoffHour) {
        StreamingResponseBody stream = out -> reportingService.streamCsvExport(
                principal, period, fromDate, toDate, branchId, paymentMethod, timezone, businessDayCutoffHour, out);

        String filename = "dineflow-financial-report-" + period.name().toLowerCase() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(stream);
    }

    @GetMapping(value = "/export/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAuthority('REPORT_FINANCIAL_EXPORT') or hasAuthority('HOTEL_ADMIN') or hasAuthority('HOTEL_OWNER')")
    public ResponseEntity<byte[]> exportPdf(
            @AuthenticationPrincipal DineFlowPrincipal principal,
            @RequestParam(value = "period", defaultValue = "DAILY") ReportPeriodType period,
            @RequestParam(value = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(value = "branchId", required = false) UUID branchId,
            @RequestParam(value = "timezone", required = false) String timezone,
            @RequestParam(value = "businessDayCutoffHour", required = false) Integer businessDayCutoffHour) {
        byte[] pdf = reportingService.generatePdfExport(
                principal, period, fromDate, toDate, branchId, timezone, businessDayCutoffHour);

        String filename = "dineflow-financial-report-" + period.name().toLowerCase() + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
