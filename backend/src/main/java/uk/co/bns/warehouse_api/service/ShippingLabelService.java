package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;
import uk.co.bns.warehouse_api.entity.Carton;
import uk.co.bns.warehouse_api.entity.Order;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.repository.CartonRepository;
import uk.co.bns.warehouse_api.repository.OrderRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Generates a clearly-marked DUMMY shipping label per carton - a placeholder ahead
 * of real courier integration (DPD label generation etc., still not built). Nothing
 * here is a scannable barcode or a genuine tracking number; the "barcode" is a
 * decorative bar pattern and the tracking number is randomly generated and stored
 * against the carton purely so the workflow has something to point at.
 */
@Service
@RequiredArgsConstructor
public class ShippingLabelService {

    private final OrderRepository orderRepository;
    private final CartonRepository cartonRepository;

    private static final float LABEL_WIDTH = 288; // 4in
    private static final float LABEL_HEIGHT = 432; // 6in
    private static final float MARGIN = 18;
    private final Random random = new Random();

    public byte[] generate(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order " + orderId + " not found"));
        List<Carton> cartons = cartonRepository.findByOrder_IdOrderByCartonNumberAsc(orderId);

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (Carton carton : cartons) {
                if (carton.getTrackingNumber() == null) {
                    carton.setTrackingNumber(generateTrackingNumber(order, carton));
                    cartonRepository.save(carton);
                }
                writeLabelPage(document, order, carton, cartons.size());
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String generateTrackingNumber(Order order, Carton carton) {
        return "DUMMY" + String.format("%010d", Math.abs(random.nextLong() % 10_000_000_000L));
    }

    private void writeLabelPage(PDDocument document, Order order, Carton carton, int totalCartons) throws IOException {
        PDPage page = new PDPage(new PDRectangle(LABEL_WIDTH, LABEL_HEIGHT));
        document.addPage(page);

        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            float y = LABEL_HEIGHT - MARGIN;

            // Unmistakable "this isn't real" banner, top and bottom.
            y = writeBanner(content, y);

            y -= 6;
            content.setLineWidth(1f);
            content.moveTo(MARGIN, y);
            content.lineTo(LABEL_WIDTH - MARGIN, y);
            content.stroke();
            y -= 20;

            y = writeLine(content, "From:", MARGIN, y, PDType1Font.HELVETICA_BOLD, 8);
            y = writeLine(content, "BNS Distribution Ltd", MARGIN, y, PDType1Font.HELVETICA, 9);
            y = writeLine(content, "Unit 8 Douglas Mill, Bradley Lane", MARGIN, y, PDType1Font.HELVETICA, 9);
            y = writeLine(content, "Standish, Wigan, Lancashire, WN6 0XF", MARGIN, y, PDType1Font.HELVETICA, 9);
            y -= 10;

            y = writeLine(content, "Deliver To:", MARGIN, y, PDType1Font.HELVETICA_BOLD, 9);
            if (order.getDeliveryName() != null) {
                y = writeLine(content, order.getDeliveryName(), MARGIN, y, PDType1Font.HELVETICA_BOLD, 13);
            }
            String townCountry = Optional.ofNullable(order.getDeliveryTown()).orElse("")
                    + (order.getDeliveryTown() != null && order.getDeliveryCountry() != null ? ", " : "")
                    + Optional.ofNullable(order.getDeliveryCountry()).orElse("");
            if (!townCountry.isBlank()) {
                y = writeLine(content, townCountry, MARGIN, y, PDType1Font.HELVETICA, 11);
            }
            String postcodeCountryCode = Optional.ofNullable(order.getDeliveryPostcode()).orElse("")
                    + (order.getDeliveryPostcode() != null && order.getDeliveryCountryCode() != null ? "  " : "")
                    + Optional.ofNullable(order.getDeliveryCountryCode()).orElse("");
            if (!postcodeCountryCode.isBlank()) {
                y = writeLine(content, postcodeCountryCode, MARGIN, y, PDType1Font.HELVETICA_BOLD, 11);
            }
            y -= 10;

            content.moveTo(MARGIN, y);
            content.lineTo(LABEL_WIDTH - MARGIN, y);
            content.stroke();
            y -= 18;

            y = writeLine(content, "Order: " + order.getOrderNumber(), MARGIN, y, PDType1Font.HELVETICA_BOLD, 11);
            y = writeLine(content, "Carton " + carton.getCartonNumber() + " of " + totalCartons, MARGIN, y, PDType1Font.HELVETICA, 10);
            java.math.BigDecimal weight = carton.getWeightKg();
            y = writeLine(content, "Weight: " + (weight != null ? weight + " kg" : "Not recorded"), MARGIN, y, PDType1Font.HELVETICA, 10);
            y = writeLine(content, "Courier: " + Optional.ofNullable(order.getCourierMethod()).orElse("Not set"), MARGIN, y, PDType1Font.HELVETICA, 10);
            y -= 16;

            y = drawFakeBarcode(content, y);
            y = writeLine(content, carton.getTrackingNumber(), MARGIN, y, PDType1Font.HELVETICA_BOLD, 10);

            writeBanner(content, MARGIN + 14);
        }
    }

    private float writeBanner(PDPageContentStream content, float y) throws IOException {
        content.setNonStrokingColor(220, 38, 38);
        content.addRect(MARGIN, y - 14, LABEL_WIDTH - 2 * MARGIN, 16);
        content.fill();
        content.setNonStrokingColor(255, 255, 255);
        content.beginText();
        content.setFont(PDType1Font.HELVETICA_BOLD, 9);
        content.newLineAtOffset(MARGIN + 8, y - 11);
        content.showText("SAMPLE LABEL - NOT FOR CARRIER USE");
        content.endText();
        content.setNonStrokingColor(0, 0, 0);
        return y - 20;
    }

    /**
     * Purely decorative bar pattern - not a real, scannable barcode. Good enough to
     * make the label look and feel complete without pretending it's carrier-ready.
     */
    private float drawFakeBarcode(PDPageContentStream content, float y) throws IOException {
        float barTop = y;
        float barHeight = 36;
        float x = MARGIN;
        float maxX = LABEL_WIDTH - MARGIN;
        content.setNonStrokingColor(0, 0, 0);
        while (x < maxX) {
            float width = 1.2f + random.nextFloat() * 2.5f;
            if (random.nextBoolean()) {
                content.addRect(x, barTop - barHeight, width, barHeight);
                content.fill();
            }
            x += width + 1.2f;
        }
        return barTop - barHeight - 12;
    }

    private float writeLine(PDPageContentStream content, String text, float x, float y, PDType1Font font, float size) throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
        return y - (size + 4);
    }
}
