package com.dineflow.invoice.service;

import com.dineflow.billing.domain.RestaurantBill;
import com.dineflow.billing.domain.RestaurantBillItem;
import com.dineflow.invoice.domain.RestaurantInvoice;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class InvoicePdfGenerator {

    private static final Logger log = LoggerFactory.getLogger(InvoicePdfGenerator.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm z")
            .withZone(ZoneId.of("UTC"));

    public byte[] generateInvoicePdf(RestaurantInvoice invoice, RestaurantBill bill) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                PDType1Font fontOblique = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

                float margin = 50;
                float y = page.getMediaBox().getHeight() - margin;

                // 1. Header: Restaurant Name & Document Title
                contentStream.beginText();
                contentStream.setFont(fontBold, 18);
                contentStream.newLineAtOffset(margin, y);
                contentStream.showText(sanitizeText(invoice.getRestaurantNameSnapshot()));
                contentStream.endText();

                contentStream.beginText();
                contentStream.setFont(fontBold, 14);
                contentStream.newLineAtOffset(page.getMediaBox().getWidth() - margin - 140, y);
                contentStream.showText("TAX INVOICE");
                contentStream.endText();

                y -= 20;

                // 2. Branch & Tax Registration Info
                if (invoice.getBranchNameSnapshot() != null || invoice.getBranchAddressSnapshot() != null) {
                    contentStream.beginText();
                    contentStream.setFont(fontRegular, 9);
                    contentStream.newLineAtOffset(margin, y);
                    String branchText = (invoice.getBranchNameSnapshot() != null ? invoice.getBranchNameSnapshot() : "")
                            + (invoice.getBranchAddressSnapshot() != null ? " • " + invoice.getBranchAddressSnapshot() : "");
                    contentStream.showText(sanitizeText(branchText));
                    contentStream.endText();
                    y -= 14;
                }

                if (invoice.getTaxRegistrationNumber() != null && !invoice.getTaxRegistrationNumber().isBlank()) {
                    contentStream.beginText();
                    contentStream.setFont(fontRegular, 9);
                    contentStream.newLineAtOffset(margin, y);
                    contentStream.showText("Tax Registration No: " + sanitizeText(invoice.getTaxRegistrationNumber()));
                    contentStream.endText();
                    y -= 14;
                }

                // 3. Invoice Meta (Number, Date, Bill Reference)
                y -= 10;
                contentStream.setLineWidth(1f);
                contentStream.moveTo(margin, y);
                contentStream.lineTo(page.getMediaBox().getWidth() - margin, y);
                contentStream.stroke();
                y -= 18;

                contentStream.beginText();
                contentStream.setFont(fontBold, 10);
                contentStream.newLineAtOffset(margin, y);
                contentStream.showText("Invoice No: " + invoice.getInvoiceNumber());
                contentStream.endText();

                contentStream.beginText();
                contentStream.setFont(fontRegular, 9);
                contentStream.newLineAtOffset(page.getMediaBox().getWidth() - margin - 180, y);
                contentStream.showText("Date: " + DATE_FORMATTER.format(invoice.getIssuedAt()));
                contentStream.endText();

                y -= 14;

                contentStream.beginText();
                contentStream.setFont(fontRegular, 9);
                contentStream.newLineAtOffset(margin, y);
                contentStream.showText("Bill Reference: " + bill.getBillNumber() + " (Table " + bill.getTableNumber() + ")");
                contentStream.endText();

                contentStream.beginText();
                contentStream.setFont(fontRegular, 9);
                contentStream.newLineAtOffset(page.getMediaBox().getWidth() - margin - 180, y);
                contentStream.showText("Payment: " + invoice.getPaymentMethod());
                contentStream.endText();

                // 4. Customer Billing Details (if present)
                if (invoice.getCustomerName() != null && !invoice.getCustomerName().isBlank()) {
                    y -= 14;
                    contentStream.beginText();
                    contentStream.setFont(fontRegular, 9);
                    contentStream.newLineAtOffset(margin, y);
                    String custText = "Billed To: " + invoice.getCustomerName()
                            + (invoice.getCustomerTaxId() != null ? " (Tax ID: " + invoice.getCustomerTaxId() + ")" : "");
                    contentStream.showText(sanitizeText(custText));
                    contentStream.endText();
                }

                y -= 20;

                // 5. Line Items Header
                contentStream.setLineWidth(0.5f);
                contentStream.moveTo(margin, y);
                contentStream.lineTo(page.getMediaBox().getWidth() - margin, y);
                contentStream.stroke();
                y -= 14;

                contentStream.beginText();
                contentStream.setFont(fontBold, 9);
                contentStream.newLineAtOffset(margin, y);
                contentStream.showText("Description");
                contentStream.endText();

                contentStream.beginText();
                contentStream.setFont(fontBold, 9);
                contentStream.newLineAtOffset(margin + 260, y);
                contentStream.showText("Qty");
                contentStream.endText();

                contentStream.beginText();
                contentStream.setFont(fontBold, 9);
                contentStream.newLineAtOffset(margin + 320, y);
                contentStream.showText("Unit Price");
                contentStream.endText();

                contentStream.beginText();
                contentStream.setFont(fontBold, 9);
                contentStream.newLineAtOffset(page.getMediaBox().getWidth() - margin - 60, y);
                contentStream.showText("Amount");
                contentStream.endText();

                y -= 8;
                contentStream.moveTo(margin, y);
                contentStream.lineTo(page.getMediaBox().getWidth() - margin, y);
                contentStream.stroke();
                y -= 16;

                // 6. Line Items Table Rows
                if (bill.getItems() != null) {
                    for (RestaurantBillItem item : bill.getItems()) {
                        contentStream.beginText();
                        contentStream.setFont(fontRegular, 9);
                        contentStream.newLineAtOffset(margin, y);
                        String desc = item.getItemNameSnapshot() + (item.getVariantNameSnapshot() != null ? " (" + item.getVariantNameSnapshot() + ")" : "");
                        contentStream.showText(sanitizeText(desc));
                        contentStream.endText();

                        contentStream.beginText();
                        contentStream.setFont(fontRegular, 9);
                        contentStream.newLineAtOffset(margin + 265, y);
                        contentStream.showText(String.valueOf(item.getQuantity()));
                        contentStream.endText();

                        contentStream.beginText();
                        contentStream.setFont(fontRegular, 9);
                        contentStream.newLineAtOffset(margin + 320, y);
                        contentStream.showText(item.getEffectiveUnitPrice().toPlainString());
                        contentStream.endText();

                        contentStream.beginText();
                        contentStream.setFont(fontRegular, 9);
                        contentStream.newLineAtOffset(page.getMediaBox().getWidth() - margin - 60, y);
                        contentStream.showText(item.getLineTotal().toPlainString());
                        contentStream.endText();

                        y -= 14;
                    }
                }

                // 7. Financial Summary Section
                y -= 10;
                contentStream.moveTo(margin + 240, y);
                contentStream.lineTo(page.getMediaBox().getWidth() - margin, y);
                contentStream.stroke();
                y -= 16;

                writeSummaryRow(contentStream, fontRegular, "Subtotal (" + invoice.getCurrencyCode() + "):", invoice.getSubtotal().toPlainString(), margin, y, page);
                y -= 14;

                if (invoice.getDiscountAmount().signum() > 0) {
                    writeSummaryRow(contentStream, fontRegular, "Discount:", "-" + invoice.getDiscountAmount().toPlainString(), margin, y, page);
                    y -= 14;
                }

                if (invoice.getServiceChargePercentage().signum() > 0) {
                    writeSummaryRow(contentStream, fontRegular, "Service Charge (" + invoice.getServiceChargePercentage() + "%):", invoice.getServiceChargeAmount().toPlainString(), margin, y, page);
                    y -= 14;
                }

                if (invoice.getTaxPercentage().signum() > 0) {
                    writeSummaryRow(contentStream, fontRegular, "Tax (" + invoice.getTaxPercentage() + "%):", invoice.getTaxAmount().toPlainString(), margin, y, page);
                    y -= 14;
                }

                y -= 4;
                contentStream.setLineWidth(1f);
                contentStream.moveTo(margin + 240, y);
                contentStream.lineTo(page.getMediaBox().getWidth() - margin, y);
                contentStream.stroke();
                y -= 16;

                writeSummaryRow(contentStream, fontBold, "Grand Total (" + invoice.getCurrencyCode() + "):", invoice.getGrandTotal().toPlainString(), margin, y, page);

                // 8. Footer Note
                contentStream.beginText();
                contentStream.setFont(fontOblique, 8);
                contentStream.newLineAtOffset(margin, 50);
                contentStream.showText("This is an electronically generated fiscal invoice. Thank you for dining with us!");
                contentStream.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Failed to generate PDF for invoice {}", invoice.getInvoiceNumber(), e);
            throw new RuntimeException("Could not generate invoice PDF", e);
        }
    }

    private void writeSummaryRow(PDPageContentStream stream, PDType1Font font, String label, String value, float margin, float y, PDPage page) throws IOException {
        stream.beginText();
        stream.setFont(font, 9);
        stream.newLineAtOffset(margin + 260, y);
        stream.showText(label);
        stream.endText();

        stream.beginText();
        stream.setFont(font, 9);
        stream.newLineAtOffset(page.getMediaBox().getWidth() - margin - 60, y);
        stream.showText(value);
        stream.endText();
    }

    private String sanitizeText(String text) {
        if (text == null) return "";
        // PDFBox Type 1 font requires ISO-8859-1 compatible characters
        return text.replaceAll("[^\\x00-\\x7F]", "?");
    }
}
