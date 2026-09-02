package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;
import uk.co.bns.warehouse_api.entity.Order;
import uk.co.bns.warehouse_api.entity.OrderLine;
import uk.co.bns.warehouse_api.entity.StockItem;
import uk.co.bns.warehouse_api.enums.StockItemStatus;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.repository.OrderRepository;
import uk.co.bns.warehouse_api.repository.StockItemRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates a picking note as an immediate PDF download - no output-method toggling,
 * no layout selection screen. One button, one document, showing exactly what a
 * warehouse operator needs: what's required, how much is available in total, the
 * product's default bin with its available quantity there, and where else it can be
 * found if the default bin comes up short.
 */
@Service
@RequiredArgsConstructor
public class PickingNoteService {

    private final OrderRepository orderRepository;
    private final StockItemRepository stockItemRepository;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter GENERATED_AT_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final float MARGIN = 40;
    private static final PDRectangle PAGE_SIZE = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
    private static final float PAGE_WIDTH = PAGE_SIZE.getWidth();
    private static final float PAGE_HEIGHT = PAGE_SIZE.getHeight();

    // Column x-offsets (relative to MARGIN), left to right: Required, SKU/Product,
    // Default Bin, Alternative Bins, Total Available, Qty Picked (handwritten box).
    private static final float COL_REQUIRED = 0;
    private static final float COL_SKU = 55;
    private static final float COL_DEFAULT_BIN = 330;
    private static final float COL_ALT_BINS = 430;
    private static final float COL_TOTAL_AVAIL = 610;
    private static final float COL_QTY_PICKED = 680;

    // A true footer - generated-at timestamp bottom-left, "page X of Y" bottom-right -
    // reserved on every page, drawn in a second pass once the total page count is known.
    private static final float FOOTER_ZONE_HEIGHT = 45;
    private static final float FOOTER_RULE_Y = 32;
    private static final float FOOTER_TEXT_Y = 18;

    // Breathing room between the last order line on a page and whatever's pinned
    // below it (totals / special instructions / footer) - large enough that even a
    // full order-line row can't dip into that reserved area.
    private static final float GAP_ABOVE_BOTTOM_STACK = 35;

    public byte[] generate(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order " + orderId + " not found"));

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            String specialInstructions = order.getSpecialInstructions();
            boolean hasInstructions = specialInstructions != null && !specialInstructions.isBlank();
            List<String> wrappedInstructions = hasInstructions
                    ? wrapText(specialInstructions, PDType1Font.HELVETICA, 10, PAGE_WIDTH - 2 * MARGIN)
                    : List.of();
            float instructionsHeight = hasInstructions ? instructionsHeight(wrappedInstructions) : 0;
            float totalsHeight = totalsHeight();

            // Reserved on every page (not just the last one) so that whichever page the
            // order lines happen to end on already has room for the totals/instructions
            // stack to sit at the same fixed distance from the bottom - pinned "footer-like"
            // positions rather than ones that float depending on how much text is on that page.
            float bottomReserve = FOOTER_ZONE_HEIGHT + GAP_ABOVE_BOTTOM_STACK + totalsHeight + instructionsHeight;

            PDPage page = new PDPage(PAGE_SIZE);
            document.addPage(page);
            PDPageContentStream content = new PDPageContentStream(document, page);
            float y = PAGE_HEIGHT - MARGIN;

            y = writeHeader(content, order, y);
            y -= 20;
            y = writeTableHeader(content, y);

            int totalQtyRequired = 0;
            BigDecimal totalWeight = BigDecimal.ZERO;

            for (OrderLine line : order.getLines()) {
                if (y < bottomReserve) {
                    content.close();
                    page = new PDPage(PAGE_SIZE);
                    document.addPage(page);
                    content = new PDPageContentStream(document, page);
                    y = PAGE_HEIGHT - MARGIN;
                    y = writeTableHeader(content, y);
                }
                y = writeLineRow(content, line, y);

                int required = Math.max(0, line.getQuantityOrdered() - line.getQuantityDespatched());
                totalQtyRequired += required;
                BigDecimal weight = line.getProduct().getWeightKg();
                if (weight != null) {
                    totalWeight = totalWeight.add(weight.multiply(BigDecimal.valueOf(required)));
                }
            }

            // Totals sit directly above the special instructions box (or directly above
            // the footer if there are none), at a fixed distance from the bottom.
            float instructionsTopY = FOOTER_ZONE_HEIGHT + instructionsHeight;
            writeTotals(content, instructionsTopY + totalsHeight, totalQtyRequired, totalWeight);

            if (hasInstructions) {
                writeSpecialInstructions(content, wrappedInstructions, instructionsHeight);
            }

            content.close();

            LocalDateTime generatedAt = LocalDateTime.now();
            int totalPages = document.getNumberOfPages();
            for (int i = 0; i < totalPages; i++) {
                writeFooter(document, document.getPage(i), generatedAt, i + 1, totalPages);
            }

            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeFooter(PDDocument document, PDPage page, LocalDateTime generatedAt, int pageNumber, int totalPages) throws IOException {
        try (PDPageContentStream footer = new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            footer.setLineWidth(0.5f);
            footer.moveTo(MARGIN, FOOTER_RULE_Y);
            footer.lineTo(PAGE_WIDTH - MARGIN, FOOTER_RULE_Y);
            footer.stroke();

            footer.beginText();
            footer.setFont(PDType1Font.HELVETICA, 8);
            footer.newLineAtOffset(MARGIN, FOOTER_TEXT_Y);
            footer.showText("Generated " + generatedAt.format(GENERATED_AT_FORMAT));
            footer.endText();

            String pageLabel = pageNumber + " of " + totalPages;
            float labelWidth = PDType1Font.HELVETICA.getStringWidth(pageLabel) / 1000 * 8;
            footer.beginText();
            footer.setFont(PDType1Font.HELVETICA, 8);
            footer.newLineAtOffset(PAGE_WIDTH - MARGIN - labelWidth, FOOTER_TEXT_Y);
            footer.showText(pageLabel);
            footer.endText();
        }
    }

    private float writeHeader(PDPageContentStream content, Order order, float y) throws IOException {
        content.beginText();
        content.setFont(PDType1Font.HELVETICA_BOLD, 18);
        content.newLineAtOffset(MARGIN, y);
        content.showText("PICKING NOTE");
        content.endText();
        y -= 28;

        // Order details (left) and delivery details (right) share the same top row,
        // with delivery starting roughly halfway across the page, rather than stacking
        // one block above the other.
        float topY = y;
        float rightColX = MARGIN + (PAGE_WIDTH - 2 * MARGIN) / 2f;

        float leftY = topY;
        leftY = writeLine(content, "Order Number: " + order.getOrderNumber(), MARGIN, leftY, PDType1Font.HELVETICA_BOLD, 11);
        leftY = writeLine(content, "Order Date: " + order.getOrderDate().format(DATE_FORMAT), MARGIN, leftY, PDType1Font.HELVETICA, 10);
        leftY = writeLine(content, "Customer: " + order.getCustomerName(), MARGIN, leftY, PDType1Font.HELVETICA, 10);
        if (order.getOrderReference() != null) {
            leftY = writeLine(content, "Order Reference: " + order.getOrderReference(), MARGIN, leftY, PDType1Font.HELVETICA, 10);
        }

        float rightY = topY;
        rightY = writeLine(content, "Deliver to:", rightColX, rightY, PDType1Font.HELVETICA_BOLD, 10);
        if (order.getDeliveryName() != null) {
            rightY = writeLine(content, order.getDeliveryName(), rightColX, rightY, PDType1Font.HELVETICA, 10);
        }
        String townCountry = Optional.ofNullable(order.getDeliveryTown()).orElse("")
                + (order.getDeliveryTown() != null && order.getDeliveryCountry() != null ? ", " : "")
                + Optional.ofNullable(order.getDeliveryCountry()).orElse("");
        if (!townCountry.isBlank()) {
            rightY = writeLine(content, townCountry, rightColX, rightY, PDType1Font.HELVETICA, 10);
        }
        String postcodeCountryCode = Optional.ofNullable(order.getDeliveryPostcode()).orElse("")
                + (order.getDeliveryPostcode() != null && order.getDeliveryCountryCode() != null ? "  " : "")
                + Optional.ofNullable(order.getDeliveryCountryCode()).orElse("");
        if (!postcodeCountryCode.isBlank()) {
            rightY = writeLine(content, postcodeCountryCode, rightColX, rightY, PDType1Font.HELVETICA, 10);
        }
        rightY -= 6;
        rightY = writeLine(content, "Courier: " + Optional.ofNullable(order.getCourierMethod()).orElse("(not set)"), rightColX, rightY, PDType1Font.HELVETICA_BOLD, 10);

        return Math.min(leftY, rightY);
    }

    /**
     * Order-wide totals (required units, total weight) - pinned directly above the
     * special instructions box (or directly above the footer if there are none), at
     * a fixed distance from the bottom of whichever page it lands on. Both figures
     * share a single row, side by side with a small gap, and there's no dividing
     * rule of its own - it reads as a compact line sitting just above whatever's
     * pinned below it.
     */
    private void writeTotals(PDPageContentStream content, float zoneTopY, int totalQtyRequired, BigDecimal totalWeight) throws IOException {
        float y = zoneTopY - 10;

        PDType1Font font = PDType1Font.HELVETICA_BOLD;
        float fontSize = 10;
        String qtyText = "Total Qty: " + totalQtyRequired;
        String weightText = "Total Weight: " + formatWeight(totalWeight) + " kg";

        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(MARGIN, y);
        content.showText(qtyText);
        content.endText();

        float qtyWidth = font.getStringWidth(qtyText) / 1000 * fontSize;
        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(MARGIN + qtyWidth + 24, y);
        content.showText(weightText);
        content.endText();
    }

    /**
     * Vertical space the totals block occupies, mirroring the offsets used in
     * writeTotals exactly so the reserved space and the drawn block agree.
     */
    private float totalsHeight() {
        return 14;
    }

    private String formatWeight(BigDecimal totalWeight) {
        return totalWeight.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * Order-level free text, pinned to a fixed distance from the bottom of whichever
     * page it lands on - not written directly after wherever the last line item
     * happened to end, so it always sits in the same "footer-like" spot regardless
     * of how much of the page the order lines filled.
     */
    private void writeSpecialInstructions(PDPageContentStream content, List<String> wrappedLines, float zoneHeight) throws IOException {
        float y = FOOTER_ZONE_HEIGHT + zoneHeight;
        y -= 6;
        content.setLineWidth(0.5f);
        content.moveTo(MARGIN, y);
        content.lineTo(PAGE_WIDTH - MARGIN, y);
        content.stroke();
        y -= 18;

        content.beginText();
        content.setFont(PDType1Font.HELVETICA_BOLD, 10);
        content.newLineAtOffset(MARGIN, y);
        content.showText("Special Instructions");
        content.endText();
        y -= 15;

        for (String line : wrappedLines) {
            content.beginText();
            content.setFont(PDType1Font.HELVETICA, 10);
            content.newLineAtOffset(MARGIN, y);
            content.showText(line);
            content.endText();
            y -= 13;
        }
    }

    /**
     * Vertical space the instructions box occupies, mirroring the offsets used in
     * writeSpecialInstructions exactly so the reserved space and the drawn box agree.
     */
    private float instructionsHeight(List<String> wrappedLines) {
        return 6 + 18 + 15 + wrappedLines.size() * 13;
    }

    /**
     * Simple greedy word-wrap sized to the given font/width, since PDFBox has no
     * built-in text wrapping.
     */
    private List<String> wrapText(String text, PDType1Font font, float fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            if (paragraph.isBlank()) {
                lines.add("");
                continue;
            }
            StringBuilder current = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                float width = font.getStringWidth(candidate) / 1000 * fontSize;
                if (width > maxWidth && !current.isEmpty()) {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    current = new StringBuilder(candidate);
                }
            }
            lines.add(current.toString());
        }
        return lines;
    }

    private float writeTableHeader(PDPageContentStream content, float y) throws IOException {
        y -= 4;
        content.setLineWidth(0.5f);
        content.moveTo(MARGIN, y);
        content.lineTo(PAGE_WIDTH - MARGIN, y);
        content.stroke();
        y -= 14;

        drawAt(content, "Required", MARGIN + COL_REQUIRED, y, PDType1Font.HELVETICA_BOLD, 9);
        drawAt(content, "SKU / Product", MARGIN + COL_SKU, y, PDType1Font.HELVETICA_BOLD, 9);
        drawAt(content, "Default Bin", MARGIN + COL_DEFAULT_BIN, y, PDType1Font.HELVETICA_BOLD, 9);
        drawAt(content, "Alternative Bins", MARGIN + COL_ALT_BINS, y, PDType1Font.HELVETICA_BOLD, 9);
        drawAt(content, "Total Avail.", MARGIN + COL_TOTAL_AVAIL, y, PDType1Font.HELVETICA_BOLD, 9);
        drawAt(content, "Qty Picked", MARGIN + COL_QTY_PICKED, y, PDType1Font.HELVETICA_BOLD, 9);

        y -= 6;
        content.moveTo(MARGIN, y);
        content.lineTo(PAGE_WIDTH - MARGIN, y);
        content.stroke();
        y -= 14;
        return y;
    }

    private float writeLineRow(PDPageContentStream content, OrderLine line, float y) throws IOException {
        float rowTopY = y;
        int required = Math.max(0, line.getQuantityOrdered() - line.getQuantityDespatched());

        List<StockItem> items = stockItemRepository.findByProduct_Id(line.getProduct().getId());
        Map<String, Integer> availableByLocation = new LinkedHashMap<>();
        for (StockItem item : items) {
            if (item.getStatus() == StockItemStatus.AVAILABLE && item.getLocation() != null) {
                availableByLocation.merge(item.getLocation().getCode(), 1, Integer::sum);
            }
        }
        int totalAvailable = availableByLocation.values().stream().mapToInt(Integer::intValue).sum();

        String defaultBinCode = line.getProduct().getDefaultLocation() != null
                ? line.getProduct().getDefaultLocation().getCode() : null;
        int defaultBinQty = defaultBinCode != null ? availableByLocation.getOrDefault(defaultBinCode, 0) : 0;
        String defaultBinText = defaultBinCode != null ? defaultBinCode + " (" + defaultBinQty + ")" : "(none set)";

        String alternativeBinsText = availableByLocation.entrySet().stream()
                .filter(e -> defaultBinCode == null || !e.getKey().equals(defaultBinCode))
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + " (" + e.getValue() + ")")
                .collect(Collectors.joining(", "));
        if (alternativeBinsText.isBlank()) alternativeBinsText = "-";

        drawAt(content, String.valueOf(required), MARGIN + COL_REQUIRED, y, PDType1Font.HELVETICA, 9);

        content.beginText();
        content.setFont(PDType1Font.HELVETICA_BOLD, 9);
        content.newLineAtOffset(MARGIN + COL_SKU, y);
        content.showText(line.getProduct().getSku());
        content.endText();

        drawAt(content, defaultBinText, MARGIN + COL_DEFAULT_BIN, y, PDType1Font.HELVETICA, 9);
        drawAt(content, truncate(alternativeBinsText, 38), MARGIN + COL_ALT_BINS, y, PDType1Font.HELVETICA, 8);
        drawAt(content, String.valueOf(totalAvailable), MARGIN + COL_TOTAL_AVAIL, y, PDType1Font.HELVETICA, 9);

        y -= 12;
        content.beginText();
        content.setFont(PDType1Font.HELVETICA, 8);
        content.newLineAtOffset(MARGIN + COL_SKU, y);
        content.showText(truncate(line.getProduct().getName(), 95));
        content.endText();

        y -= 16;

        // Empty box for the warehouse operator to write the quantity actually picked as
        // they go, spanning the row's full height so there's room for a legible number.
        float boxWidth = 46;
        float boxHeight = 22;
        float boxX = MARGIN + COL_QTY_PICKED;
        float boxY = rowTopY - boxHeight + 6;
        content.addRect(boxX, boxY, boxWidth, boxHeight);
        content.stroke();

        return y;
    }

    private void drawAt(PDPageContentStream content, String text, float x, float y, PDType1Font font, float size) throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
    }

    private float writeLine(PDPageContentStream content, String text, float x, float y, PDType1Font font, float size) throws IOException {
        drawAt(content, text, x, y, font, size);
        return y - (size + 4);
    }

    private String truncate(String text, int max) {
        if (text.length() <= max) return text;
        return text.substring(0, max - 1) + "\u2026";
    }
}
