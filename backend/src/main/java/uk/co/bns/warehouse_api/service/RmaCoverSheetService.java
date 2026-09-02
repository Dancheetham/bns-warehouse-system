package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;
import uk.co.bns.warehouse_api.entity.RmaItem;
import uk.co.bns.warehouse_api.entity.RmaRequest;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.repository.RmaRequestRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RmaCoverSheetService {

    private final RmaRequestRepository rmaRequestRepository;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final float MARGIN = 40;
    private static final PDRectangle PAGE_SIZE = PDRectangle.A4;
    private static final float PAGE_WIDTH = PAGE_SIZE.getWidth();
    private static final float PAGE_HEIGHT = PAGE_SIZE.getHeight();

    public byte[] generate(Long rmaRequestId) {
        RmaRequest rma = rmaRequestRepository.findById(rmaRequestId)
                .orElseThrow(() -> new NotFoundException("RMA " + rmaRequestId + " not found"));
        if (rma.getRmaNumber() == null) {
            throw new uk.co.bns.warehouse_api.exception.ValidationException("This RMA hasn't been approved yet - no RMA number assigned");
        }

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PAGE_SIZE);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float y = PAGE_HEIGHT - MARGIN;

                content.beginText();
                content.setFont(PDType1Font.HELVETICA_BOLD, 20);
                content.newLineAtOffset(MARGIN, y);
                content.showText("RMA Request Form");
                content.endText();
                y -= 20;
                y = writeLine(content, "BNS RMA Number: " + rma.getRmaNumber(), MARGIN, y, PDType1Font.HELVETICA_BOLD, 13);
                y = writeLine(content, "RMA Issue Date: " + (rma.getApprovedAt() != null ? rma.getApprovedAt().format(DATE_FORMAT) : ""),
                        MARGIN, y, PDType1Font.HELVETICA, 10);
                y -= 10;

                content.moveTo(MARGIN, y);
                content.lineTo(PAGE_WIDTH - MARGIN, y);
                content.stroke();
                y -= 20;

                float rightColX = MARGIN + (PAGE_WIDTH - 2 * MARGIN) / 2f;
                float leftY = y;
                leftY = writeLine(content, "Customer:", MARGIN, leftY, PDType1Font.HELVETICA_BOLD, 10);
                leftY = writeLine(content, rma.getCustomerName(), MARGIN, leftY, PDType1Font.HELVETICA, 10);
                if (rma.getCustomerCompany() != null) {
                    leftY = writeLine(content, rma.getCustomerCompany(), MARGIN, leftY, PDType1Font.HELVETICA, 10);
                }
                if (rma.getCustomerAddress() != null) {
                    for (String line : rma.getCustomerAddress().split("\n")) {
                        leftY = writeLine(content, line, MARGIN, leftY, PDType1Font.HELVETICA, 10);
                    }
                }

                float rightY = y;
                rightY = writeLine(content, "Contact:", rightColX, rightY, PDType1Font.HELVETICA_BOLD, 10);
                rightY = writeLine(content, Optional.ofNullable(rma.getContactName()).orElse("-"), rightColX, rightY, PDType1Font.HELVETICA, 10);
                rightY = writeLine(content, Optional.ofNullable(rma.getContactPhone()).orElse(""), rightColX, rightY, PDType1Font.HELVETICA, 10);
                rightY = writeLine(content, Optional.ofNullable(rma.getContactEmail()).orElse(""), rightColX, rightY, PDType1Font.HELVETICA, 10);
                if (rma.getOriginalOrder() != null) {
                    rightY = writeLine(content, "Original order: " + rma.getOriginalOrder().getOrderNumber(), rightColX, rightY, PDType1Font.HELVETICA, 10);
                }

                y = Math.min(leftY, rightY) - 14;
                content.moveTo(MARGIN, y);
                content.lineTo(PAGE_WIDTH - MARGIN, y);
                content.stroke();
                y -= 18;

                float colPart = MARGIN, colDesc = MARGIN + 90, colId = MARGIN + 260, colQty = MARGIN + 380,
                        colFaulty = MARGIN + 420, colTicket = MARGIN + 470;
                drawAt(content, "Part Code", colPart, y, PDType1Font.HELVETICA_BOLD, 9);
                drawAt(content, "Description", colDesc, y, PDType1Font.HELVETICA_BOLD, 9);
                drawAt(content, "MAC / Serial", colId, y, PDType1Font.HELVETICA_BOLD, 9);
                drawAt(content, "Qty", colQty, y, PDType1Font.HELVETICA_BOLD, 9);
                drawAt(content, "Faulty", colFaulty, y, PDType1Font.HELVETICA_BOLD, 9);
                drawAt(content, "GS Ticket", colTicket, y, PDType1Font.HELVETICA_BOLD, 9);
                y -= 6;
                content.moveTo(MARGIN, y);
                content.lineTo(PAGE_WIDTH - MARGIN, y);
                content.stroke();
                y -= 14;

                for (RmaItem item : rma.getItems()) {
                    drawAt(content, truncate(item.getProduct().getSku(), 14), colPart, y, PDType1Font.HELVETICA, 9);
                    drawAt(content, truncate(item.getProduct().getName(), 26), colDesc, y, PDType1Font.HELVETICA, 9);
                    drawAt(content, truncate(Optional.ofNullable(item.getIdentifier()).orElse("-"), 20), colId, y, PDType1Font.HELVETICA, 9);
                    drawAt(content, String.valueOf(item.getQuantity()), colQty, y, PDType1Font.HELVETICA, 9);
                    drawAt(content, item.getFaulty() ? "Yes" : "No", colFaulty, y, PDType1Font.HELVETICA, 9);
                    drawAt(content, Optional.ofNullable(item.getGrandstreamTicketNumber()).orElse("-"), colTicket, y, PDType1Font.HELVETICA, 9);
                    y -= 13;
                    if (item.getReasonForReturn() != null && !item.getReasonForReturn().isBlank()) {
                        drawAt(content, "Reason: " + truncate(item.getReasonForReturn(), 100), colPart, y, PDType1Font.HELVETICA, 8);
                        y -= 13;
                    }
                    y -= 4;
                }

                y -= 10;
                content.moveTo(MARGIN, y);
                content.lineTo(PAGE_WIDTH - MARGIN, y);
                content.stroke();
                y -= 20;

                y = writeLine(content, "Return items to:", MARGIN, y, PDType1Font.HELVETICA_BOLD, 10);
                y = writeLine(content, "BNS Distribution Ltd", MARGIN, y, PDType1Font.HELVETICA, 10);
                y = writeLine(content, "Unit 8 Douglas Mill, Bradley Lane", MARGIN, y, PDType1Font.HELVETICA, 10);
                y = writeLine(content, "Standish, Wigan, Lancashire, WN6 0XF", MARGIN, y, PDType1Font.HELVETICA, 10);
                y -= 10;
                y = writeLine(content, "Please enclose this document within the return.", MARGIN, y, PDType1Font.HELVETICA_OBLIQUE, 9);
                if (rma.getItems().stream().anyMatch(RmaItem::getFaulty)) {
                    y = writeLine(content,
                            "Faulty items with an approved Grandstream ticket do not need to be returned in original packaging.",
                            MARGIN, y, PDType1Font.HELVETICA_OBLIQUE, 9);
                }
                if (rma.getItems().stream().anyMatch(i -> !i.getFaulty())) {
                    y = writeLine(content,
                            "Non-faulty returns must be in new, resaleable condition with all accessories and protective film -",
                            MARGIN, y, PDType1Font.HELVETICA_OBLIQUE, 9);
                    y = writeLine(content, "failing this may result in a 15% restocking fee.", MARGIN, y, PDType1Font.HELVETICA_OBLIQUE, 9);
                }
            }

            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void drawAt(PDPageContentStream content, String text, float x, float y, PDType1Font font, float size) throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
    }

    private float writeLine(PDPageContentStream content, String text, float x, float y, PDType1Font font, float size) throws IOException {
        if (text != null && !text.isBlank()) {
            drawAt(content, text, x, y, font, size);
        }
        return y - (size + 4);
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        return text.substring(0, max - 1) + "\u2026";
    }
}
