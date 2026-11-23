package com.dineflow.reporting.service;

import com.dineflow.reporting.dto.FinancialReportResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;

@Service
public class FinancialReportPdfGenerator {

    public byte[] generatePdf(FinancialReportResponse report, String restaurantName) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

                float margin = 48;
                float y = page.getMediaBox().getHeight() - margin;

                // Title Header
                cs.beginText();
                cs.setFont(fontBold, 16);
                cs.newLineAtOffset(margin, y);
                cs.showText(sanitize(restaurantName + " - Financial Summary Report"));
                cs.endText();
                y -= 18;

                cs.beginText();
                cs.setFont(fontRegular, 9);
                cs.newLineAtOffset(margin, y);
                cs.showText(sanitize(String.format("Period: %s (%s to %s) | Timezone: %s | Cutoff Hour: %02d:00",
                        report.periodType(), report.fromDate(), report.toDate(), report.timezone(), report.businessDayCutoffHour())));
                cs.endText();
                y -= 16;

                drawLine(cs, margin, page.getMediaBox().getWidth() - margin, y);
                y -= 20;

                // Section 1: Revenue & Billing KPIs
                y = writeSectionHeading(cs, fontBold, margin, y, "1. Sales & Billing Summary (" + report.currencyCode() + ")");
                y = writeKeyValue(cs, fontRegular, fontBold, margin, y, "Issued Bills (Non-Voided)", String.valueOf(report.billCount()));
                y = writeKeyValue(cs, fontRegular, fontBold, margin, y, "Gross Sales (Pre-Discount Subtotal)", fmt(report.currencyCode(), report.grossSales()));
                y = writeKeyValue(cs, fontRegular, fontBold, margin, y, "Total Discounts Applied", fmt(report.currencyCode(), report.discountTotal()));
                y = writeKeyValue(cs, fontRegular, fontBold, margin, y, "Net Sales (Gross Sales - Discounts)", fmt(report.currencyCode(), report.netSales()));
                y = writeKeyValue(cs, fontRegular, fontBold, margin, y, "Service Charges Collected", fmt(report.currencyCode(), report.serviceChargeTotal()));
                y = writeKeyValue(cs, fontRegular, fontBold, margin, y, "Tax Collected", fmt(report.currencyCode(), report.taxTotal()));
                y = writeKeyValue(cs, fontRegular, fontBold, margin, y, "Total Billed Value (Grand Total)", fmt(report.currencyCode(), report.totalBillValue()));
                y = writeKeyValue(cs, fontRegular, fontBold, margin, y, "Average Bill Value", fmt(report.currencyCode(), report.averageBillValue()));
                y -= 12;

                // Section 2: Confirmed Collections & Refunds
                y = writeSectionHeading(cs, fontBold, margin, y, "2. Confirmed Collections, Refunds & Outstanding");
                y = writeKeyValue(cs, fontRegular, fontBold, margin, y, "Confirmed Collections (All Methods)", fmt(report.currencyCode(), report.confirmedCollectionTotal()));
                y = writeKeyValue(cs, fontRegular, fontBold, margin, y, "  - Cash / Counter Collection", fmt(report.currencyCode(), report.cashCollectionTotal()));
                y = writeKeyValue(cs, fontRegular, fontBold, margin, y, "  - Online Payment Collection", fmt(report.currencyCode(), report.onlineCollectionTotal()));
                y = writeKeyValue(cs, fontRegular, fontBold, margin, y, "Completed Refunds (" + report.refundCount() + ")", fmt(report.currencyCode(), report.refundTotal()));
                y = writeKeyValue(cs, fontRegular, fontBold, margin, y, "Net Confirmed Collection (Collections - Refunds)", fmt(report.currencyCode(), report.netConfirmedCollection()));
                y = writeKeyValue(cs, fontRegular, fontBold, margin, y, "Outstanding Unpaid Balance (" + report.outstandingBillCount() + " bills)", fmt(report.currencyCode(), report.outstandingBalance()));
                y = writeKeyValue(cs, fontRegular, fontBold, margin, y, "Cancelled Orders (" + report.cancelledOrderCount() + ")", fmt(report.currencyCode(), report.cancelledOrderValue()));
                y -= 12;

                // Section 3: Payment Method Breakdown
                if (!report.paymentMethodBreakdown().isEmpty() && y > 140) {
                    y = writeSectionHeading(cs, fontBold, margin, y, "3. Payment Method Breakdown");
                    for (var pm : report.paymentMethodBreakdown()) {
                        y = writeKeyValue(cs, fontRegular, fontBold, margin, y,
                                pm.paymentMethod() + " (" + pm.transactionCount() + " txns)",
                                fmt(report.currencyCode(), pm.confirmedAmount()));
                    }
                    y -= 10;
                }

                // Section 4: Branch Comparison
                if (!report.branchBreakdown().isEmpty() && y > 120) {
                    y = writeSectionHeading(cs, fontBold, margin, y, "4. Branch-Wise Performance");
                    for (var b : report.branchBreakdown()) {
                        if (y < 70) break;
                        y = writeKeyValue(cs, fontRegular, fontBold, margin, y,
                                b.branchName() + " (" + b.billCount() + " bills)",
                                "Net Sales: " + fmt(report.currencyCode(), b.netSales()) + " | Collected: " + fmt(report.currencyCode(), b.netConfirmedCollection()));
                    }
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to generate financial summary PDF", ex);
        }
    }

    private float writeSectionHeading(PDPageContentStream cs, PDType1Font fontBold, float x, float y, String title) throws IOException {
        cs.beginText();
        cs.setFont(fontBold, 11);
        cs.newLineAtOffset(x, y);
        cs.showText(sanitize(title));
        cs.endText();
        return y - 16;
    }

    private float writeKeyValue(PDPageContentStream cs, PDType1Font fontRegular, PDType1Font fontBold, float x, float y, String label, String value) throws IOException {
        cs.beginText();
        cs.setFont(fontRegular, 9);
        cs.newLineAtOffset(x, y);
        cs.showText(sanitize(label));
        cs.endText();

        cs.beginText();
        cs.setFont(fontBold, 9);
        cs.newLineAtOffset(x + 280, y);
        cs.showText(sanitize(value));
        cs.endText();
        return y - 14;
    }

    private void drawLine(PDPageContentStream cs, float x1, float x2, float y) throws IOException {
        cs.setLineWidth(0.75f);
        cs.moveTo(x1, y);
        cs.lineTo(x2, y);
        cs.stroke();
    }

    private String fmt(String currency, BigDecimal amount) {
        return currency + " " + (amount != null ? amount.toPlainString() : "0.00");
    }

    private String sanitize(String text) {
        if (text == null) return "";
        return text.replaceAll("[^\\x20-\\x7E]", " ");
    }
}
