package uk.co.bns.warehouse_api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.co.bns.warehouse_api.dto.DpdLabelResult;
import uk.co.bns.warehouse_api.dto.DpdShipmentResult;
import uk.co.bns.warehouse_api.entity.Order;
import uk.co.bns.warehouse_api.entity.OrderLine;
import uk.co.bns.warehouse_api.exception.ValidationException;
import uk.co.bns.warehouse_api.repository.OrderRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds and sends DPD "domestic" shipment requests - the one endpoint that
 * covers UK, Northern Ireland, the Republic of Ireland and the Channel
 * Islands as outbound destinations, which is everywhere BNS ships to.
 *
 * Field mapping is taken directly from DPD's own OpenAPI schema for
 * POST /v1/customer/shipping/shipments/domestic (see the project's
 * dpd-api-findings.md for the full reference). The Ireland-specific customs
 * declaration (generateCustomsData + the invoice block) is only populated
 * when the delivery country code needs it - every other destination sends a
 * plain shipment with no customs data at all.
 */
@Service
@RequiredArgsConstructor
public class DpdShippingService {

    private static final Logger log = LoggerFactory.getLogger(DpdShippingService.class);

    // Countries DPD's "domestic" endpoint covers that still need a full
    // customs declaration despite being a domestic-network movement.
    private static final Set<String> REQUIRES_CUSTOMS_DATA = Set.of("IE");

    private final DpdAuthService dpdAuthService;
    private final SettingsService settingsService;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    // Deliberately NOT @Transactional. When this is called from inside
    // DespatchService's own @Transactional confirmDespatch (auto-booking at
    // despatch), a @Transactional method here that throws marks the shared
    // transaction rollback-only the instant the exception crosses this
    // method's proxy boundary - before DespatchService's try/catch ever gets
    // a chance to handle it and let despatch continue. That's exactly what
    // caused "Transaction silently rolled back because it has been marked as
    // rollback-only": the despatch itself (stock movements, order status)
    // got wiped out by a DPD failure that was supposed to be best-effort and
    // non-blocking. The single order save below still runs in its own
    // implicit transaction via Spring Data either way.
    public DpdShipmentResult createShipment(Order order) {
        validateOrder(order);

        ObjectNode body = buildRequestBody(order);

        HttpRequest request = HttpRequest.newBuilder(URI.create(dpdAuthService.baseUrl() + "/v1/customer/shipping/shipments/domestic"))
                .header("Authorization", "Bearer " + dpdAuthService.getAccessToken())
                .header("Client-Id", dpdAuthService.apiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        JsonNode responseBody = send(request);
        JsonNode data = responseBody.has("data") ? responseBody.get("data") : responseBody;

        String shipmentId = data.path("shipmentId").asText(null);
        JsonNode firstConsignment = data.path("consignments").isArray() && data.path("consignments").size() > 0
                ? data.path("consignments").get(0) : null;
        String consignmentNumber = firstConsignment != null ? firstConsignment.path("consignmentNumber").asText(null) : null;
        List<String> parcelNumbers = firstConsignment != null && firstConsignment.path("parcelNumber").isArray()
                ? objectMapper.convertValue(firstConsignment.path("parcelNumber"), List.class)
                : List.of();

        order.setDpdShipmentId(shipmentId);
        order.setDpdConsignmentNumber(consignmentNumber);
        order.setDpdParcelNumbers(String.join(",", parcelNumbers));
        order.setDpdShippedAt(LocalDateTime.now());
        orderRepository.save(order);

        log.info("Booked DPD shipment for order {}: shipmentId={} consignmentNumber={} parcels={}",
                order.getOrderNumber(), shipmentId, consignmentNumber, parcelNumbers);

        return new DpdShipmentResult(shipmentId, consignmentNumber, parcelNumbers);
    }

    /**
     * printerType/printerDpi follow whatever label printer this account uses;
     * format 3 (application/vnd.zebra-zpl... requested via Accept) covers the
     * common thermal label printers we've seen so far. Returns the raw label
     * data as DPD sends it (already in the requested format), ready to hand
     * to the existing print-agent flow unchanged.
     */
    public DpdLabelResult getLabels(Order order, int printerType, int printerDpi, String acceptFormat) {
        if (order.getDpdShipmentId() == null) {
            throw new ValidationException("Order " + order.getOrderNumber() + " has no DPD shipment booked yet");
        }
        String url = dpdAuthService.baseUrl() + "/v1/customer/shipping/shipments/" + order.getDpdShipmentId()
                + "/labels?printerType=" + printerType + "&printerDpi=" + printerDpi;

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + dpdAuthService.getAccessToken())
                .header("Client-Id", dpdAuthService.apiKey())
                .header("Accept", acceptFormat)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Failed to fetch DPD labels for order {} - DPD returned {}: {}",
                        order.getOrderNumber(), response.statusCode(), response.body());
                throw new RuntimeException("Failed to fetch DPD labels - DPD returned HTTP " + response.statusCode());
            }
            return new DpdLabelResult(response.body());
        } catch (java.io.IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Failed to fetch DPD labels: " + e.getMessage(), e);
        }
    }

    private void validateOrder(Order order) {
        if (order.getDeliveryAddressLine1() == null || order.getDeliveryAddressLine1().isBlank()) {
            throw new ValidationException("Order " + order.getOrderNumber() + " has no delivery address line 1 set - add one before booking a DPD shipment");
        }
        if (order.getDeliveryPostcode() == null || order.getDeliveryPostcode().isBlank()) {
            throw new ValidationException("Order " + order.getOrderNumber() + " has no delivery postcode set");
        }
        if (order.getDeliveryCountryCode() == null || order.getDeliveryCountryCode().isBlank()) {
            throw new ValidationException("Order " + order.getOrderNumber() + " has no delivery country code set");
        }
        if (requiresCustomsData(order) && (settingsService.get("dpd_eori_number", "").isBlank())) {
            throw new ValidationException("An EORI number must be set under Settings > DPD before shipping to " + order.getDeliveryCountryCode());
        }
        if (requiresCustomsData(order)) {
            List<String> missingCommodityCodes = order.getLines().stream()
                    .filter(l -> l.getProduct().getCommodityCode() == null || l.getProduct().getCommodityCode().isBlank())
                    .map(l -> l.getProduct().getSku())
                    .collect(Collectors.toList());
            if (!missingCommodityCodes.isEmpty()) {
                throw new ValidationException("These products need a commodity code before shipping to "
                        + order.getDeliveryCountryCode() + ": " + String.join(", ", missingCommodityCodes));
            }
        }
    }

    private boolean requiresCustomsData(Order order) {
        return REQUIRES_CUSTOMS_DATA.contains(order.getDeliveryCountryCode().toUpperCase());
    }

    private ObjectNode buildRequestBody(Order order) {
        ObjectNode root = objectMapper.createObjectNode();

        String senderOrganisation = settingsService.get("dpd_sender_organisation", "");
        String senderStreet = settingsService.get("dpd_sender_street", "");
        String senderTown = settingsService.get("dpd_sender_town", "");
        String senderPostcode = settingsService.get("dpd_sender_postcode", "");
        String senderCountryCode = settingsService.get("dpd_sender_country_code", "GB");
        String senderContactName = settingsService.get("dpd_sender_contact_name", "");
        String senderContactPhone = settingsService.get("dpd_sender_contact_phone", "");
        String senderContactEmail = settingsService.get("dpd_sender_contact_email", "");

        ObjectNode consignment = root.putObject("outboundConsignment");

        ObjectNode collectionDetails = consignment.putObject("collectionDetails");
        collectionDetails.put("organisation", senderOrganisation);
        collectionDetails.put("street", senderStreet);
        collectionDetails.put("town", senderTown);
        collectionDetails.put("postcode", senderPostcode);
        collectionDetails.put("countryCode", senderCountryCode);
        collectionDetails.put("contactName", senderContactName);
        collectionDetails.put("telephone", senderContactPhone);

        ObjectNode deliveryDetails = consignment.putObject("deliveryDetails");
        ObjectNode deliveryContact = deliveryDetails.putObject("contactDetails");
        deliveryContact.put("contactName", order.getDeliveryName());
        deliveryContact.put("telephone", order.getDeliveryPhone() != null ? order.getDeliveryPhone() : "");

        ObjectNode deliveryAddress = deliveryDetails.putObject("address");
        deliveryAddress.put("organisation", order.getDeliveryName() != null ? order.getDeliveryName() : "");
        deliveryAddress.put("street", order.getDeliveryAddressLine1());
        if (order.getDeliveryAddressLine2() != null && !order.getDeliveryAddressLine2().isBlank()) {
            deliveryAddress.put("locality", order.getDeliveryAddressLine2());
        }
        deliveryAddress.put("town", order.getDeliveryTown() != null ? order.getDeliveryTown() : "");
        deliveryAddress.put("postcode", order.getDeliveryPostcode());
        deliveryAddress.put("countryCode", order.getDeliveryCountryCode().toUpperCase());

        String networkCode = settingsService.get("dpd_network_code", "");
        if (!networkCode.isBlank()) {
            consignment.put("networkCode", networkCode);
        }
        consignment.put("numberOfParcels", 1);
        consignment.put("totalWeight", totalWeightKg(order).doubleValue());
        consignment.put("shippingRef1", order.getOrderNumber());
        if (order.getOrderReference() != null) {
            consignment.put("shippingRef2", order.getOrderReference());
        }

        ArrayNode parcels = consignment.putArray("parcels");
        ObjectNode parcel = parcels.addObject();
        parcel.put("sequenceNumber", 1);
        parcel.put("weight", totalWeightKg(order).doubleValue());

        if (requiresCustomsData(order)) {
            root.put("generateCustomsData", true);
            ObjectNode invoice = root.putObject("invoice");
            invoice.put("invoiceType", 2); // Commercial
            invoice.put("exportReason", "01"); // Sale

            ObjectNode exporterDetails = invoice.putObject("exporterDetails");
            exporterDetails.put("organisation", senderOrganisation);
            exporterDetails.put("street", senderStreet);
            exporterDetails.put("town", senderTown);
            exporterDetails.put("postcode", senderPostcode);
            exporterDetails.put("countryCode", senderCountryCode);
            exporterDetails.put("contactName", senderContactName);
            exporterDetails.put("telephone", senderContactPhone);
            exporterDetails.put("email", senderContactEmail);
            exporterDetails.put("eoriNumber", settingsService.get("dpd_eori_number", ""));

            ObjectNode importerDetails = invoice.putObject("importerDetails");
            importerDetails.put("organisation", order.getDeliveryName() != null ? order.getDeliveryName() : "");
            importerDetails.put("street", order.getDeliveryAddressLine1());
            importerDetails.put("town", order.getDeliveryTown() != null ? order.getDeliveryTown() : "");
            importerDetails.put("postcode", order.getDeliveryPostcode());
            importerDetails.put("countryCode", order.getDeliveryCountryCode().toUpperCase());
            importerDetails.put("telephone", order.getDeliveryPhone() != null ? order.getDeliveryPhone() : "");

            ArrayNode products = parcel.putArray("products");
            for (OrderLine line : order.getLines()) {
                ObjectNode productNode = products.addObject();
                productNode.put("productDescription", line.getProduct().getName());
                productNode.put("productQty", line.getQuantityOrdered());
                productNode.put("unitWeight", line.getProduct().getWeightKg() != null
                        ? line.getProduct().getWeightKg().doubleValue() : 0.0);
                productNode.put("unitValue", line.getUnitPrice() != null ? line.getUnitPrice().doubleValue() : 0.0);
                productNode.put("commodityCode", line.getProduct().getCommodityCode());
                productNode.put("countryOfOrigin", line.getProduct().getCountryOfOrigin());
            }
        }

        return root;
    }

    private java.math.BigDecimal totalWeightKg(Order order) {
        return order.getLines().stream()
                .map(l -> (l.getProduct().getWeightKg() != null ? l.getProduct().getWeightKg() : java.math.BigDecimal.ZERO)
                        .multiply(java.math.BigDecimal.valueOf(l.getQuantityOrdered())))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }

    private JsonNode send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return objectMapper.readTree(response.body());
            }
            log.error("DPD shipment creation failed - DPD returned {}: {}", response.statusCode(), response.body());
            throw new ValidationException(describeDpdError(response.body(), response.statusCode()));
        } catch (java.io.IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Failed to create DPD shipment: " + e.getMessage(), e);
        }
    }

    /**
     * DPD's error body is {"error": [{"message", "fieldName", ...}, ...]} -
     * surfaces the actual field-level messages rather than a bare status code,
     * since these are almost always a fixable data problem (missing field,
     * bad postcode format, etc).
     */
    private String describeDpdError(String responseBody, int statusCode) {
        try {
            JsonNode parsed = objectMapper.readTree(responseBody);
            JsonNode errors = parsed.path("error");
            if (errors.isArray() && errors.size() > 0) {
                StringBuilder sb = new StringBuilder("DPD rejected the shipment: ");
                for (int i = 0; i < errors.size(); i++) {
                    if (i > 0) sb.append("; ");
                    JsonNode err = errors.get(i);
                    sb.append(err.path("message").asText(err.toString()));
                    if (!err.path("fieldName").isMissingNode() && !err.path("fieldName").asText().isBlank()) {
                        sb.append(" (").append(err.path("fieldName").asText()).append(")");
                    }
                }
                return sb.toString();
            }
        } catch (Exception ignored) {
            // fall through to the generic message below
        }
        return "DPD rejected the shipment (HTTP " + statusCode + ")";
    }
}
