package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;
import uk.co.bns.warehouse_api.entity.Invoice;
import uk.co.bns.warehouse_api.entity.InvoiceLine;
import uk.co.bns.warehouse_api.enums.InvoiceType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/**
 * Renders the Invoice entity built by InvoiceService into a PDF, in the same
 * plain hand-drawn PDFBox style as RmaCoverSheetService - BNS's own address
 * comes from the existing DPD sender Settings (Settings > DPD), since that's
 * already BNS's real business address rather than duplicating it; bank
 * details are optional and only shown if actually configured (Settings >
 * Invoicing) - never fabricated.
 */
@Service
@RequiredArgsConstructor
public class InvoicePdfService {

    private final SettingsService settingsService;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final float MARGIN = 40;
    private static final PDRectangle PAGE_SIZE = PDRectangle.A4;
    private static final float PAGE_WIDTH = PAGE_SIZE.getWidth();
    private static final float PAGE_HEIGHT = PAGE_SIZE.getHeight();
    private static final float ROW_HEIGHT = 16;

    public byte[] generate(Invoice invoice) {
        boolean isCredit = invoice.getInvoiceType() == InvoiceType.CREDIT_NOTE;
        String docLabel = isCredit ? "CREDIT NOTE" : "INVOICE";

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = newPage(document);
            PDPageContentStream content = new PDPageContentStream(document, page);
            float y = PAGE_HEIGHT - MARGIN;

            // ---- Letterhead ----
            y = writeLine(content, settingsService.get("dpd_sender_organisation", "BNS Distribution Ltd"),
                    MARGIN, y, PDType1Font.HELVETICA_BOLD, 16);
            float addrY = y;
            addrY = writeLine(content, settingsService.get("dpd_sender_street", ""), MARGIN, addrY, PDType1Font.HELVETICA, 9);
            addrY = writeLine(content, settingsService.get("dpd_sender_locality", ""), MARGIN, addrY, PDType1Font.HELVETICA, 9);
            addrY = writeLine(content, joinNonBlank(", ", settingsService.get("dpd_sender_town", ""),
                    settingsService.get("dpd_sender_county", "")), MARGIN, addrY, PDType1Font.HELVETICA, 9);
            addrY = writeLine(content, settingsService.get("dpd_sender_postcode", ""), MARGIN, addrY, PDType1Font.HELVETICA, 9);
            addrY = writeLine(content, settingsService.get("dpd_sender_contact_phone", ""), MARGIN, addrY, PDType1Font.HELVETICA, 9);
            addrY = writeLine(content, settingsService.get("invoice_company_email", ""), MARGIN, addrY, PDType1Font.HELVETICA, 9);

            // ---- Document box (top right) ----
            float boxX = PAGE_WIDTH - MARGIN - 200;
            float boxY = y;
            content.setLineWidth(1f);
            content.addRect(boxX, boxY - 90, 200, 106);
            content.stroke();
            float innerY = boxY + 8;
            innerY = writeLine(content, docLabel, boxX + 10, innerY, PDType1Font.HELVETICA_BOLD, 13);
            innerY -= 4;
            innerY = writeLabelValue(content, (isCredit ? "Credit Note No:" : "Invoice No:"),
                    String.valueOf(invoice.getInvoiceNumber()), boxX + 10, innerY);
            innerY = writeLabelValue(content, "Date:", invoice.getGenerationDate().format(DATE_FORMAT), boxX + 10, innerY);
            innerY = writeLabelValue(content, "Account No:",
                    orDash(invoice.getCompany().getAccountNumber()), boxX + 10, innerY);
            if (invoice.getCompany().getVatNumber() != null && !invoice.getCompany().getVatNumber().isBlank()) {
                writeLabelValue(content, "Customer VAT No:", invoice.getCompany().getVatNumber(), boxX + 10, innerY);
            }

            y = Math.min(addrY, boxY - 100) - 14;
            content.moveTo(MARGIN, y);
            content.lineTo(PAGE_WIDTH - MARGIN, y);
            content.stroke();
            y -= 18;

            // ---- Customer block ----
            y = writeLine(content, "Customer:", MARGIN, y, PDType1Font.HELVETICA_BOLD, 10);
            y = writeLine(content, invoice.getCustomerName(), MARGIN, y, PDType1Font.HELVETICA, 10);
            y -= 10;
            content.moveTo(MARGIN, y);
            content.lineTo(PAGE_WIDTH - MARGIN, y);
            content.stroke();
            y -= 18;

            // ---- Line items table ----
            float colOrder = MARGIN, colSku = MARGIN + 90, colDesc = MARGIN + 170, colQty = PAGE_WIDTH - MARGIN - 190,
                    colPrice = PAGE_WIDTH - MARGIN - 150, colNet = PAGE_WIDTH - MARGIN - 95, colVat = PAGE_WIDTH - MARGIN - 40;
            drawAt(content, "Order", colOrder, y, PDType1Font.HELVETICA_BOLD, 8);
            drawAt(content, "SKU", colSku, y, PDType1Font.HELVETICA_BOLD, 8);
            drawAt(content, "Description", colDesc, y, PDType1Font.HELVETICA_BOLD, 8);
            drawAt(content, "Qty", colQty, y, PDType1Font.HELVETICA_BOLD, 8);
            drawAt(content, "Price", colPrice, y, PDType1Font.HELVETICA_BOLD, 8);
            drawAt(content, "Net", colNet, y, PDType1Font.HELVETICA_BOLD, 8);
            drawAt(content, "VAT", colVat, y, PDType1Font.HELVETICA_BOLD, 8);
            y -= 6;
            content.moveTo(MARGIN, y);
            content.lineTo(PAGE_WIDTH - MARGIN, y);
            content.stroke();
            y -= 14;

            for (InvoiceLine line : invoice.getLines()) {
                if (y < MARGIN + 140) {
                    content.close();
                    page = newPage(document);
                    content = new PDPageContentStream(document, page);
                    y = PAGE_HEIGHT - MARGIN;
                }
                drawAt(content, truncate(line.getOrder().getOrderNumber(), 12), colOrder, y, PDType1Font.HELVETICA, 8);
                drawAt(content, truncate(line.getSku(), 14), colSku, y, PDType1Font.HELVETICA, 8);
                drawAt(content, truncate(line.getDescription(), 34), colDesc, y, PDType1Font.HELVETICA, 8);
                drawAt(content, String.valueOf(line.getQuantity()), colQty, y, PDType1Font.HELVETICA, 8);
                drawAt(content, money(line.getUnitPrice()), colPrice, y, PDType1Font.HELVETICA, 8);
                drawAt(content, money(line.getNetAmount()), colNet, y, PDType1Font.HELVETICA, 8);
                drawAt(content, money(line.getVatAmount()), colVat, y, PDType1Font.HELVETICA, 8);
                y -= ROW_HEIGHT;
            }

            y -= 6;
            content.moveTo(MARGIN, y);
            content.lineTo(PAGE_WIDTH - MARGIN, y);
            content.stroke();
            y -= 20;

            if (y < MARGIN + 160) {
                content.close();
                page = newPage(document);
                content = new PDPageContentStream(document, page);
                y = PAGE_HEIGHT - MARGIN;
            }

            // ---- Totals ----
            float totalsX = PAGE_WIDTH - MARGIN - 190;
            y = writeLabelValue(content, "Net:", money(invoice.getNetTotal()), totalsX, y);
            y = writeLabelValue(content, "VAT (" + invoice.getVatRate().stripTrailingZeros().toPlainString() + "%):",
                    money(invoice.getVatTotal()), totalsX, y);
            y = writeLabelValue(content, "Total:", money(invoice.getGrandTotal()), totalsX, y, PDType1Font.HELVETICA_BOLD);
            y -= 20;

            String terms = settingsService.get("invoice_terms", "30 days from invoice date");
            if (!terms.isBlank()) {
                y = writeLine(content, "Terms: " + terms, MARGIN, y, PDType1Font.HELVETICA, 9);
            }

            // ---- Bank details (optional - only if actually configured) ----
            String bankName = settingsService.get("invoice_bank_account_name", "");
            if (!bankName.isBlank()) {
                y -= 10;
                content.moveTo(MARGIN, y);
                content.lineTo(PAGE_WIDTH - MARGIN, y);
                content.stroke();
                y -= 18;
                y = writeLine(content, "Bank details:", MARGIN, y, PDType1Font.HELVETICA_BOLD, 9);
                y = writeLine(content, bankName, MARGIN, y, PDType1Font.HELVETICA, 9);
                y = writeLine(content, joinNonBlank(" / ",
                        labelIfSet("A/C", settingsService.get("invoice_bank_account_number", "")),
                        labelIfSet("S/C", settingsService.get("invoice_bank_sort_code", ""))),
                        MARGIN, y, PDType1Font.HELVETICA, 9);
                y = writeLine(content, labelIfSet("IBAN", settingsService.get("invoice_bank_iban", "")),
                        MARGIN, y, PDType1Font.HELVETICA, 9);
                y = writeLine(content, labelIfSet("Swift/BIC", settingsService.get("invoice_bank_swift_bic", "")),
                        MARGIN, y, PDType1Font.HELVETICA, 9);
            }

            // ---- Footer ----
            float footerY = MARGIN + 20;
            String vatRegNo = settingsService.get("dpd_sender_vat_number", "");
            String companyRegNo = settingsService.get("invoice_company_reg_number", "");
            if (!vatRegNo.isBlank()) {
                writeLine(content, "VAT Registration No: " + vatRegNo, MARGIN, footerY, PDType1Font.HELVETICA, 8);
            }
            if (!companyRegNo.isBlank()) {
                writeLine(content, "Company Reg No: " + companyRegNo, MARGIN, footerY - 11, PDType1Font.HELVETICA, 8);
            }

            content.close();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private PDPage newPage(PDDocument document) {
        PDPage page = new PDPage(PAGE_SIZE);
        document.addPage(page);
        return page;
    }

    private void drawAt(PDPageContentStream content, String text, float x, float y, PDType1Font font, float size) throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(text == null ? "" : text);
        content.endText();
    }

    private float writeLine(PDPageContentStream content, String text, float x, float y, PDType1Font font, float size) throws IOException {
        if (text != null && !text.isBlank()) {
            drawAt(content, text, x, y, font, size);
        }
        return y - (size + 4);
    }

    private float writeLabelValue(PDPageContentStream content, String label, String value, float x, float y) throws IOException {
        return writeLabelValue(content, label, value, x, y, PDType1Font.HELVETICA);
    }

    private float writeLabelValue(PDPageContentStream content, String label, String value, float x, float y, PDType1Font valueFont) throws IOException {
        drawAt(content, label, x, y, PDType1Font.HELVETICA, 9);
        drawAt(content, value, x + 95, y, valueFont, 9);
        return y - 14;
    }

    private String money(BigDecimal amount) {
        return "£" + amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String orDash(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }

    private String labelIfSet(String label, String value) {
        return (value == null || value.isBlank()) ? "" : (label + ": " + value);
    }

    private String joinNonBlank(String separator, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            if (sb.length() > 0) sb.append(separator);
            sb.append(part);
        }
        return sb.toString();
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        return text.substring(0, max - 1) + "…";
    }
}
