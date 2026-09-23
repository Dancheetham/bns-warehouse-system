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
import uk.co.bns.warehouse_api.entity.Carton;
import uk.co.bns.warehouse_api.entity.CartonLine;
import uk.co.bns.warehouse_api.entity.Company;
import uk.co.bns.warehouse_api.entity.Order;
import uk.co.bns.warehouse_api.entity.OrderLine;
import uk.co.bns.warehouse_api.entity.Product;
import uk.co.bns.warehouse_api.entity.StockItem;
import uk.co.bns.warehouse_api.exception.ValidationException;
import uk.co.bns.warehouse_api.repository.CartonLineRepository;
import uk.co.bns.warehouse_api.repository.CartonRepository;
import uk.co.bns.warehouse_api.repository.OrderRepository;
import uk.co.bns.warehouse_api.repository.StockItemRepository;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    // Only a starting point for Settings > DPD, never a silent default worth
    // relying on - DPD warn that generic contents descriptions ("tools",
    // "clothing") get parcels delayed or returned at customs, so this is
    // meant to be edited to whatever genuinely describes what BNS ships.
    static final String DEFAULT_GOODS_DESCRIPTION = "Telecoms and networking equipment";

    private final DpdAuthService dpdAuthService;
    private final SettingsService settingsService;
    private final OrderRepository orderRepository;
    private final CartonRepository cartonRepository;
    private final CartonLineRepository cartonLineRepository;
    private final StockItemRepository stockItemRepository;
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
     * common thermal label printers we've seen so far.
     *
     * Always asks DPD for the JSON array form of the response
     * (`Accept: application/json`), not the raw-format Accept header
     * (`acceptFormat`, still passed in so printerType/format stay explicit
     * for callers even though it's no longer sent as the actual header) -
     * DPD's own docs say the plain raw response is a single response body,
     * while the JSON form returns one raw label string per parcel. A
     * multi-carton shipment has one label per parcel booked, and the plain
     * form was only ever returning one of them regardless of how many
     * parcels the shipment actually had - this is what was producing only
     * one label for a 2+ carton order even after the booking itself
     * correctly sent multiple parcels. The label strings (already in
     * printerType's format - ZPL/EPL/CLP are self-contained per label, so
     * concatenation is exactly what's needed) are joined into one payload,
     * ready for the existing print-agent flow unchanged.
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
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Failed to fetch DPD labels for order {} - DPD returned {}: {}",
                        order.getOrderNumber(), response.statusCode(), response.body());
                throw new RuntimeException("Failed to fetch DPD labels - DPD returned HTTP " + response.statusCode());
            }
            return new DpdLabelResult(joinLabelStrings(response.body(), order));
        } catch (java.io.IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Failed to fetch DPD labels: " + e.getMessage(), e);
        }
    }

    /**
     * DPD's real JSON label response, confirmed from a live raw response
     * (previously only guessed at from the docs), is:
     * `{"data": {"printString": ["<label 1 raw ZPL>", "<label 2 raw ZPL>", ...]}}`
     * - an object with a "printString" array nested inside "data", one
     * entry per parcel - not a bare array directly under "data" as first
     * assumed. That wrong assumption is exactly why every despatch on a
     * shipment using this response shape was hitting the "unexpected label
     * response shape" hard-failure added as a safety net rather than ever
     * printing anything. Both shapes are now handled: the confirmed real
     * one ("data.printString" array), and the originally-assumed one ("data"
     * itself being an array or a plain string) kept as a fallback in case a
     * different shipment type or a future DPD change returns that instead.
     * Concatenates every entry so the caller gets one blob covering every
     * parcel. Only falls back to treating the response as one already-raw
     * label when it doesn't look like JSON at all (DPD sent the old-style
     * plain response despite the Accept header); anything that looks like
     * JSON but isn't one of the recognised shapes is a hard failure rather
     * than a best-effort fallback - see the comment inline for why.
     */
    private String joinLabelStrings(String responseBody, Order order) {
        boolean looksLikeJson = !responseBody.isBlank()
                && (responseBody.stripLeading().startsWith("{") || responseBody.stripLeading().startsWith("["));
        try {
            JsonNode parsed = objectMapper.readTree(responseBody);
            JsonNode data = parsed.has("data") ? parsed.get("data") : parsed;
            JsonNode labelArray = data.has("printString") ? data.get("printString") : data;
            if (labelArray.isArray()) {
                StringBuilder combined = new StringBuilder();
                for (JsonNode label : labelArray) {
                    combined.append(label.asText(""));
                }
                if (combined.length() == 0) {
                    log.warn("DPD returned an empty label array for order {}", order.getOrderNumber());
                }
                return combined.toString();
            }
            if (labelArray.isTextual()) {
                return labelArray.asText();
            }
            // Parsed as JSON fine, but not one of the recognised shapes -
            // printing the raw JSON text (field names, braces, escaped \n's
            // and all) straight to a label printer produces exactly the
            // "garbled, overlapping, stray \n characters" kind of output
            // this guards against, so this is a hard failure rather than a
            // best-effort fallback. The raw response is included directly
            // in the error (not just the server log) so it's visible from
            // Bug Reports without needing to go and check the container logs.
            log.error("DPD's label response for order {} parsed as JSON but wasn't the expected shape: {}",
                    order.getOrderNumber(), responseBody);
            throw new RuntimeException("DPD returned an unexpected label response shape - not printed, to avoid sending garbage to the label printer. Raw response: "
                    + truncate(responseBody, 1000));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            // Genuinely not JSON at all - only safe to fall back to the raw
            // body as-is when it doesn't even look like it was trying to be
            // JSON (i.e. DPD's Accept:application/json request still got a
            // plain raw label body back, which the docs suggest shouldn't
            // happen but isn't dangerous to print if it does).
            if (looksLikeJson) {
                log.error("DPD's label response for order {} looked like JSON but failed to parse: {}",
                        order.getOrderNumber(), e.getMessage());
                throw new RuntimeException("DPD's label response couldn't be read - not printed, to avoid sending garbage to the label printer: "
                        + e.getMessage() + " | Raw response: " + truncate(responseBody, 1000), e);
            }
            return responseBody;
        }
    }

    /**
     * The label format the warehouse's configured thermal printer actually
     * needs - raw ZPL (Zebra's own label command language, printerType 3),
     * not HTML and not a PDF. Sent straight to the printer with no browser
     * or PDF viewer in between, the label always comes out at the label
     * stock's real physical size - HTML/PDF rendering has no idea what size
     * label is loaded and scales to a full page instead, which is what was
     * producing labels that didn't fit.
     */
    public DpdLabelResult getLabelsForPrint(Order order) {
        int dpi;
        try {
            dpi = Integer.parseInt(settingsService.get("dpd_label_printer_dpi", "203").trim());
        } catch (NumberFormatException e) {
            dpi = 203;
        }
        return getLabels(order, 3, dpi, "text/vnd.zebra-zpl");
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
        // DPD rejects every domestic shipment - not just customs ones - with
        // "Delivery contact telephone number (outbound) is mandatory" if
        // this is blank. Catching it here means a missing phone number on
        // the order shows up as a clear, specific message rather than DPD's
        // raw rejection text.
        if (order.getDeliveryPhone() == null || order.getDeliveryPhone().isBlank()) {
            throw new ValidationException("Order " + order.getOrderNumber() + " has no delivery phone number set - DPD requires one on every shipment");
        }
        if (requiresCustomsData(order) && (settingsService.get("dpd_eori_number", "").isBlank())) {
            throw new ValidationException("An EORI number must be set under Settings > DPD before shipping to " + order.getDeliveryCountryCode());
        }
        if (requiresCustomsData(order)) {
            // The customs "exporterDetails" block is deliberately BNS's own
            // address, contact and (GB-only) EORI - not the order's Company.
            // DPD's own EORI rule ("must be a GB EORI, no EU EORIs accepted")
            // ties the customs declaration to whoever holds the DPD account
            // and is physically handing the parcel to DPD, which is BNS,
            // regardless of which customer the order is for or who it's
            // being distributed on behalf of. That's why this reads from
            // Settings > DPD rather than from Company - see the reply this
            // validation was added alongside for the fuller reasoning.
            List<String> missingSenderFields = new java.util.ArrayList<>();
            if (settingsService.get("dpd_sender_organisation", "").isBlank()) missingSenderFields.add("organisation");
            if (settingsService.get("dpd_sender_street", "").isBlank()) missingSenderFields.add("address line 1");
            if (settingsService.get("dpd_sender_town", "").isBlank()) missingSenderFields.add("address line 3 (town)");
            if (settingsService.get("dpd_sender_postcode", "").isBlank()) missingSenderFields.add("postcode");
            if (settingsService.get("dpd_sender_contact_name", "").isBlank()) missingSenderFields.add("contact name");
            if (settingsService.get("dpd_sender_contact_phone", "").isBlank()) missingSenderFields.add("contact phone");
            if (!missingSenderFields.isEmpty()) {
                throw new ValidationException("Your own sender address is needed for the customs declaration before shipping to "
                        + order.getDeliveryCountryCode() + " - fill in " + String.join(", ", missingSenderFields)
                        + " under Settings > DPD");
            }
            List<String> missingCommodityCodes = order.getLines().stream()
                    .filter(l -> l.getProduct().getCommodityCode() == null || l.getProduct().getCommodityCode().isBlank())
                    .map(l -> l.getProduct().getSku())
                    .collect(Collectors.toList());
            if (!missingCommodityCodes.isEmpty()) {
                throw new ValidationException("These products need a commodity code before shipping to "
                        + order.getDeliveryCountryCode() + ": " + String.join(", ", missingCommodityCodes));
            }
            // The importer's EORI is DPD's requirement for B2B customs
            // clearance, not BNS's - so this only applies when the order is
            // actually linked to a Company (a direct consumer sale has no
            // "business" on the receiving end for DPD to ask for). A company
            // with no EORI on file gets a clear pointer to where to add it,
            // rather than a raw DPD customs rejection at booking time.
            if (order.getCompany() != null
                    && (order.getCompany().getEoriNumber() == null || order.getCompany().getEoriNumber().isBlank())) {
                throw new ValidationException("An EORI number is needed for " + order.getCompany().getName()
                        + " before shipping to " + order.getDeliveryCountryCode() + " - add one on the Companies page");
            }
            // DPD's "Delivery Description" - the contents of the consignment
            // as a whole. Blank means DPD rejects the shipment outright.
            if (settingsService.get("dpd_goods_description", DEFAULT_GOODS_DESCRIPTION).isBlank()) {
                throw new ValidationException("A goods description is needed for the customs declaration before shipping to "
                        + order.getDeliveryCountryCode() + " - set one under Settings > DPD");
            }
            // DPD return parcels to the sender when the declared customs
            // value is zero, so an order with no prices on its lines must
            // not be booked at all.
            if (customsValue(order).compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException("Order " + order.getOrderNumber()
                        + " has no value on its lines - DPD needs a customs value above zero for "
                        + order.getDeliveryCountryCode() + ", so add unit prices before booking");
            }
        }
    }

    private boolean requiresCustomsData(Order order) {
        return REQUIRES_CUSTOMS_DATA.contains(order.getDeliveryCountryCode().toUpperCase());
    }

    private static final DateTimeFormatter DPD_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private ObjectNode buildRequestBody(Order order) {
        ObjectNode root = objectMapper.createObjectNode();

        // Required by DPD on every shipment - "date of shipment and
        // approximate time of parcel collection". Booking happens at the
        // point of despatch, so "now" is the right collection time; DPD
        // rejects the request outright with "Shipment Date is mandatory" if
        // this is left out.
        root.put("shipmentDate", LocalDateTime.now().format(DPD_DATE_TIME));

        String senderOrganisation = settingsService.get("dpd_sender_organisation", "");
        String senderStreet = settingsService.get("dpd_sender_street", "");
        String senderLocality = settingsService.get("dpd_sender_locality", "");
        String senderTown = settingsService.get("dpd_sender_town", "");
        String senderCounty = settingsService.get("dpd_sender_county", "");
        String senderPostcode = settingsService.get("dpd_sender_postcode", "");
        String senderCountryCode = settingsService.get("dpd_sender_country_code", "GB");
        String senderContactName = settingsService.get("dpd_sender_contact_name", "");
        String senderContactPhone = settingsService.get("dpd_sender_contact_phone", "");

        ObjectNode consignment = root.putObject("outboundConsignment");

        // DPD nests the address and contact under collectionDetails rather
        // than taking them flat - sending them flat (as this used to) means
        // DPD silently ignores them entirely and falls back to whatever
        // collection address is set on the account, which is why UK
        // shipments still worked despite this being wrong. Same nesting
        // applies to deliveryDetails (already correct below) and to the
        // customs invoice's exporter/importer blocks further down.
        ObjectNode collectionDetails = consignment.putObject("collectionDetails");
        putSenderAddress(collectionDetails.putObject("address"), senderOrganisation, senderStreet,
                senderLocality, senderTown, senderCounty, senderPostcode, senderCountryCode);
        ObjectNode collectionContact = collectionDetails.putObject("contactDetails");
        collectionContact.put("contactName", senderContactName);
        collectionContact.put("telephone", dpdPhone(senderContactPhone));

        ObjectNode deliveryDetails = consignment.putObject("deliveryDetails");
        ObjectNode deliveryContact = deliveryDetails.putObject("contactDetails");
        deliveryContact.put("contactName", order.getDeliveryName());
        deliveryContact.put("telephone", dpdPhone(order.getDeliveryPhone()));

        ObjectNode deliveryAddress = deliveryDetails.putObject("address");
        deliveryAddress.put("organisation", order.getDeliveryName() != null ? order.getDeliveryName() : "");
        deliveryAddress.put("street", order.getDeliveryAddressLine1());
        if (order.getDeliveryAddressLine2() != null && !order.getDeliveryAddressLine2().isBlank()) {
            deliveryAddress.put("locality", order.getDeliveryAddressLine2());
        }
        deliveryAddress.put("town", order.getDeliveryTown() != null ? order.getDeliveryTown() : "");
        deliveryAddress.put("postcode", order.getDeliveryPostcode());
        deliveryAddress.put("countryCode", order.getDeliveryCountryCode().toUpperCase());

        consignment.put("networkCode", resolveNetworkCode(order, senderPostcode, senderTown, senderCountryCode));
        consignment.put("shippingRef1", order.getOrderNumber());
        if (order.getOrderReference() != null) {
            consignment.put("shippingRef2", order.getOrderReference());
        }

        // One DPD parcel (and one label) per physical carton the order was
        // actually packed into - previously this was hardcoded to a single
        // parcel regardless of how many cartons packing created, which is why
        // a 2-carton order only ever produced 1 label. When packing hasn't
        // happened yet (e.g. booking DPD manually from the order screen
        // before despatch), there's no carton to go by, so this falls back
        // to exactly the old behaviour: a single parcel covering the whole
        // order's weight.
        List<Carton> cartons = cartonRepository.findByOrder_IdOrderByCartonNumberAsc(order.getId());
        boolean requiresCustoms = requiresCustomsData(order);
        ArrayNode parcels = consignment.putArray("parcels");
        BigDecimal totalWeight;

        if (!cartons.isEmpty()) {
            totalWeight = BigDecimal.ZERO;
            int seq = 1;
            for (Carton carton : cartons) {
                Map<Product, ProductQty> contents = productQuantitiesForCarton(carton);
                // A carton's weight is meant to be the actual scale reading, but
                // that's a manual entry staff may not always have filled in - if
                // it's missing, fall back to the sum of what's actually in this
                // carton (same basis the whole-order fallback below already
                // used) rather than sending DPD a zero-weight parcel.
                BigDecimal weight = carton.getWeightKg() != null ? carton.getWeightKg() : sumProductWeight(contents);
                totalWeight = totalWeight.add(weight);
                ObjectNode parcel = parcels.addObject();
                parcel.put("sequenceNumber", seq++);
                parcel.put("weight", weight.doubleValue());
                if (requiresCustoms) {
                    addCustomsProducts(parcel, contents);
                }
            }
            consignment.put("numberOfParcels", cartons.size());
        } else {
            totalWeight = totalWeightKg(order);
            ObjectNode parcel = parcels.addObject();
            parcel.put("sequenceNumber", 1);
            parcel.put("weight", totalWeight.doubleValue());
            if (requiresCustoms) {
                Map<Product, ProductQty> wholeOrder = new LinkedHashMap<>();
                for (OrderLine line : order.getLines()) {
                    wholeOrder.merge(line.getProduct(), new ProductQty(line.getQuantityOrdered(), line.getUnitPrice()),
                            (a, b) -> new ProductQty(a.quantity() + b.quantity(), a.unitPrice()));
                }
                addCustomsProducts(parcel, wholeOrder);
            }
            consignment.put("numberOfParcels", 1);
        }
        consignment.put("totalWeight", totalWeight.doubleValue());

        // DPD defaults this to whatever is configured in myDPD if it's left
        // out, but they "highly recommend" declaring it explicitly so customs
        // declarations can't end up denominated in the wrong currency.
        consignment.put("currency", settingsService.get("dpd_currency", "GBP"));

        if (requiresCustoms) {
            // DPD require a description of the whole consignment's contents
            // ("Delivery Description is mandatory" otherwise) for every
            // non-UK destination, separate from the per-product descriptions
            // inside parcels[].products[]. They explicitly warn that vague
            // descriptions cause customs delays or the parcel being returned,
            // which is why this is an editable setting rather than something
            // generic hardcoded here. Capped at DPD's 45-character limit.
            String goodsDescription = settingsService.get("dpd_goods_description", DEFAULT_GOODS_DESCRIPTION);
            consignment.put("deliveryDescription",
                    goodsDescription.length() > 45 ? goodsDescription.substring(0, 45) : goodsDescription);

            // The "intrinsic" value of the goods: ex-VAT and excluding
            // shipping, which is exactly what the order lines hold. DPD
            // return parcels to the sender if this is zero, so validateOrder
            // refuses to book rather than letting that happen.
            consignment.put("customsValue", customsValue(order).doubleValue());
        }

        if (requiresCustoms) {
            root.put("generateCustomsData", true);
            ObjectNode invoice = root.putObject("invoice");
            invoice.put("invoiceType", 2); // Commercial
            invoice.put("exportReason", "01"); // Sale
            // BNS always ships duties/taxes-unpaid (the receiver settles any
            // import charges directly with DPD) rather than the prepaid DT1
            // arrangement, which needs separate account setup DPD has not
            // done for us.
            invoice.put("termsOfDelivery", "DAP");

            // Both of these need their address and contact nested in their
            // own objects, exactly like collectionDetails/deliveryDetails
            // above. Sending them flat is what produced DPD's "Exporter
            // address is mandatory" rejection even with every Settings >
            // DPD field filled in - DPD was reading an exporterDetails with
            // no address object in it at all.
            ObjectNode exporterDetails = invoice.putObject("exporterDetails");
            putSenderAddress(exporterDetails.putObject("address"), senderOrganisation, senderStreet,
                    senderLocality, senderTown, senderCounty, senderPostcode, senderCountryCode);
            ObjectNode exporterContact = exporterDetails.putObject("contactDetails");
            exporterContact.put("contactName", senderContactName);
            exporterContact.put("telephone", dpdPhone(senderContactPhone));
            exporterDetails.put("eoriNumber", settingsService.get("dpd_eori_number", ""));
            String senderVatNumber = settingsService.get("dpd_sender_vat_number", "");
            if (!senderVatNumber.isBlank()) {
                exporterDetails.put("vatNumber", senderVatNumber);
            }

            ObjectNode importerDetails = invoice.putObject("importerDetails");
            ObjectNode importerAddress = importerDetails.putObject("address");
            importerAddress.put("organisation", order.getDeliveryName() != null ? order.getDeliveryName() : "");
            importerAddress.put("street", order.getDeliveryAddressLine1());
            if (order.getDeliveryAddressLine2() != null && !order.getDeliveryAddressLine2().isBlank()) {
                importerAddress.put("locality", order.getDeliveryAddressLine2());
            }
            importerAddress.put("town", order.getDeliveryTown() != null ? order.getDeliveryTown() : "");
            importerAddress.put("postcode", order.getDeliveryPostcode());
            importerAddress.put("countryCode", order.getDeliveryCountryCode().toUpperCase());
            ObjectNode importerContact = importerDetails.putObject("contactDetails");
            importerContact.put("contactName", order.getDeliveryName() != null ? order.getDeliveryName() : "");
            importerContact.put("telephone", dpdPhone(order.getDeliveryPhone()));

            // Unlike exporterDetails (always BNS's own GB EORI from
            // Settings), the importer of record for customs purposes is
            // whichever company BNS is distributing on behalf of for this
            // order - so its EORI/VAT come from the order's linked Company,
            // not Settings or the order itself. DPD only requires these for
            // B2B (business) deliveries; when the order has no linked
            // company (e.g. a direct consumer sale) they're simply omitted,
            // which is fine for a B2C shipment.
            Company company = order.getCompany();
            if (company != null && company.getEoriNumber() != null && !company.getEoriNumber().isBlank()) {
                importerDetails.put("eoriNumber", company.getEoriNumber());
                importerDetails.put("isBusiness", true);
            }
            if (company != null && company.getVatNumber() != null && !company.getVatNumber().isBlank()) {
                importerDetails.put("vatNumber", company.getVatNumber());
            }
        }

        return root;
    }

    /**
     * BNS's own address, in DPD's address shape. Used in three places in one
     * request (the collection address, and the customs invoice's exporter
     * address) so it's worth keeping in one place - they must agree, and DPD
     * rejects the shipment if the exporter address is incomplete.
     *
     * DPD's four address lines are named street / locality / town / county
     * rather than "address line 1-4", and only street, town and countryCode
     * are mandatory. The optional ones are left out entirely when blank
     * rather than sent as empty strings, since DPD length-validates whatever
     * it's given.
     */
    private void putSenderAddress(ObjectNode address, String organisation, String street, String locality,
                                  String town, String county, String postcode, String countryCode) {
        if (organisation != null && !organisation.isBlank()) {
            address.put("organisation", organisation);
        }
        address.put("street", street);
        if (locality != null && !locality.isBlank()) {
            address.put("locality", locality);
        }
        address.put("town", town);
        if (county != null && !county.isBlank()) {
            address.put("county", county);
        }
        if (postcode != null && !postcode.isBlank()) {
            address.put("postcode", postcode);
        }
        address.put("countryCode", countryCode != null && !countryCode.isBlank()
                ? countryCode.toUpperCase() : "GB");
    }

    /**
     * DPD's telephone fields are validated against ^([+]\d{1,14}|\d{0,15})$ -
     * digits only, optionally with a leading "+", and nothing else. A number
     * typed the way people actually write them ("0121 500 2500",
     * "+353 (0)1 234 5678") fails that outright, so strip it down to what
     * DPD will accept rather than having the whole shipment rejected over
     * punctuation. Anything past the 15-character limit is trimmed too.
     */
    private String dpdPhone(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.trim();
        boolean international = trimmed.startsWith("+");
        String digits = trimmed.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return "";
        }
        if (international) {
            if (digits.length() > 14) {
                digits = digits.substring(0, 14);
            }
            return "+" + digits;
        }
        return digits.length() > 15 ? digits.substring(0, 15) : digits;
    }

    /**
     * The consignment's "intrinsic" customs value: goods only, ex-VAT and
     * excluding shipping, which is what the order lines already hold. DPD
     * are explicit that a zero value gets the parcel returned to sender, so
     * validateOrder blocks booking rather than sending one.
     */
    private BigDecimal customsValue(Order order) {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderLine line : order.getLines()) {
            if (line.getUnitPrice() == null) continue;
            total = total.add(line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantityOrdered())));
        }
        return total;
    }

    /** A product's quantity and per-unit price within one parcel's customs declaration. */
    private record ProductQty(int quantity, BigDecimal unitPrice) {}

    private BigDecimal sumProductWeight(Map<Product, ProductQty> contents) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Product, ProductQty> entry : contents.entrySet()) {
            BigDecimal unitWeight = entry.getKey().getWeightKg() != null ? entry.getKey().getWeightKg() : BigDecimal.ZERO;
            total = total.add(unitWeight.multiply(BigDecimal.valueOf(entry.getValue().quantity())));
        }
        return total;
    }

    /**
     * What's actually inside one packed carton, by product - works for both
     * packing modes: SPLIT mode slices an OrderLine's quantity across cartons
     * via CartonLine, SERIAL mode assigns individual StockItems straight to a
     * Carton. Tries CartonLine first and only falls back to StockItem so a
     * carton packed under either mode resolves correctly without needing to
     * know which mode was active when it was packed.
     */
    private Map<Product, ProductQty> productQuantitiesForCarton(Carton carton) {
        Map<Product, ProductQty> quantities = new LinkedHashMap<>();
        List<CartonLine> cartonLines = cartonLineRepository.findByCarton_Id(carton.getId());
        if (!cartonLines.isEmpty()) {
            for (CartonLine cl : cartonLines) {
                Product product = cl.getOrderLine().getProduct();
                quantities.merge(product, new ProductQty(cl.getQuantity(), cl.getOrderLine().getUnitPrice()),
                        (a, b) -> new ProductQty(a.quantity() + b.quantity(), a.unitPrice()));
            }
            return quantities;
        }
        List<StockItem> items = stockItemRepository.findByCarton_Id(carton.getId());
        for (StockItem item : items) {
            Product product = item.getProduct();
            BigDecimal unitPrice = item.getOrderLine() != null ? item.getOrderLine().getUnitPrice() : null;
            quantities.merge(product, new ProductQty(1, unitPrice),
                    (a, b) -> new ProductQty(a.quantity() + b.quantity(), a.unitPrice() != null ? a.unitPrice() : b.unitPrice()));
        }
        return quantities;
    }

    private void addCustomsProducts(ObjectNode parcel, Map<Product, ProductQty> quantities) {
        ArrayNode products = parcel.putArray("products");
        for (Map.Entry<Product, ProductQty> entry : quantities.entrySet()) {
            Product product = entry.getKey();
            ProductQty qty = entry.getValue();
            ObjectNode productNode = products.addObject();
            productNode.put("productDescription", product.getName());
            productNode.put("productQty", qty.quantity());
            productNode.put("unitWeight", product.getWeightKg() != null ? product.getWeightKg().doubleValue() : 0.0);
            productNode.put("unitValue", qty.unitPrice() != null ? qty.unitPrice().doubleValue() : 0.0);
            productNode.put("commodityCode", product.getCommodityCode());
            productNode.put("countryOfOrigin", product.getCountryOfOrigin());
        }
    }

    /**
     * networkCode is a required field on every shipment, but DPD is explicit
     * that it must never be hardcoded - it's looked up per shipment via their
     * "validate outbound services" endpoint, which returns whichever services
     * are actually available between the collection and delivery postcodes
     * for this weight. The old approach (a single fixed code typed into
     * Settings) is exactly what DPD's own docs warn against, and is what was
     * producing "Failed to query network" - either nothing was sent, or a
     * code was sent that didn't necessarily match a real, currently-available
     * service for this route.
     *
     * The Settings > DPD "default network/service code" value, if set, is
     * just a fallback preference for when nothing was picked on the order
     * itself: if it matches one of the services DPD actually returns as
     * available (compared against its networkKey), that one is used;
     * otherwise the first available service is used.
     *
     * Whatever was picked on the Sales Activity/order screen's Service
     * dropdown (order.dpdNetworkKey) is treated as authoritative and is
     * booked exactly as chosen - it is never re-validated against, or
     * silently swapped for, whatever this lookup returns "available" right
     * now. That re-validation used to be able to override the picked
     * service (e.g. because the weight this lookup sees differs from what
     * was true when the choice was made), which meant the courier shown on
     * the picking note could end up not being what actually got booked.
     * Staff pick the service deliberately - it's what tells the picker
     * which courier the order is going out on - so it's respected as-is.
     */
    private String resolveNetworkCode(Order order, String senderPostcode, String senderTown, String senderCountryCode) {
        String orderChoice = order.getDpdNetworkKey();
        if (orderChoice != null && !orderChoice.isBlank()) {
            return orderChoice;
        }

        // No service was picked on the order - fall back to a live lookup so
        // there's still something valid to book against.
        JsonNode services = fetchAvailableServices(order, senderPostcode, senderTown, senderCountryCode);
        if (!services.isArray() || services.isEmpty()) {
            throw new ValidationException("DPD has no available shipping service between " + senderPostcode
                    + " and " + order.getDeliveryPostcode() + " - check both postcodes and the collection address in Settings > DPD");
        }

        String preferred = settingsService.get("dpd_network_code", "");
        if (!preferred.isBlank() && matches(services, preferred)) {
            return preferred;
        }
        return services.get(0).path("networkKey").asText();
    }

    private boolean matches(JsonNode services, String networkKey) {
        for (JsonNode service : services) {
            if (networkKey.equals(service.path("networkKey").asText(null))) {
                return true;
            }
        }
        return false;
    }

    // Where the last successfully-fetched live service list is cached (as
    // JSON), so the dropdown still has real, previously-offered options to
    // show when a later live lookup fails - see listAvailableServices().
    private static final String LAST_KNOWN_SERVICES_KEY = "dpd_last_known_services";

    /**
     * The full list of services DPD actually has available right now for an
     * order's delivery address and weight - used to populate the "Service"
     * dropdown on the order screen so staff pick from what's real, rather
     * than typing a code that may not apply to this particular route.
     *
     * When the live call itself fails (DPD unreachable, auth problem, a rate
     * limit, etc - as opposed to DPD legitimately having nothing for this
     * address), the dropdown would otherwise be forced back to free text,
     * which is exactly what staff can't be expected to get right from
     * memory. So on failure this falls back to the last list DPD returned
     * successfully for ANY order, cached in Settings - genuinely real
     * services this account has been offered before, just not re-verified
     * for this specific address/weight - with `live` set to false and
     * `liveError` explaining what went wrong, so the UI can show that
     * plainly instead of silently passing off a stale list as current.
     */
    public uk.co.bns.warehouse_api.dto.DpdServiceLookupResult listAvailableServices(Order order) {
        if (order.getDeliveryPostcode() == null || order.getDeliveryPostcode().isBlank()
                || order.getDeliveryCountryCode() == null || order.getDeliveryCountryCode().isBlank()) {
            throw new ValidationException("This order needs a delivery postcode and country before DPD services can be looked up");
        }

        String senderPostcode = settingsService.get("dpd_sender_postcode", "");
        String senderTown = settingsService.get("dpd_sender_town", "");
        String senderCountryCode = settingsService.get("dpd_sender_country_code", "GB");

        try {
            JsonNode services = fetchAvailableServices(order, senderPostcode, senderTown, senderCountryCode);
            List<uk.co.bns.warehouse_api.dto.DpdServiceOption> options = new java.util.ArrayList<>();
            if (services.isArray()) {
                for (JsonNode service : services) {
                    options.add(new uk.co.bns.warehouse_api.dto.DpdServiceOption(
                            service.path("networkKey").asText(null),
                            service.path("networkDesc").asText(""),
                            service.path("service").path("serviceDesc").asText("")));
                }
            }
            if (!options.isEmpty()) {
                cacheLastKnownServices(options);
            }
            return new uk.co.bns.warehouse_api.dto.DpdServiceLookupResult(options, true, null);
        } catch (Exception e) {
            log.warn("Live DPD service lookup failed for order {}: {}", order.getOrderNumber(), e.getMessage());
            List<uk.co.bns.warehouse_api.dto.DpdServiceOption> cached = loadLastKnownServices();
            return new uk.co.bns.warehouse_api.dto.DpdServiceLookupResult(cached, false, e.getMessage());
        }
    }

    private void cacheLastKnownServices(List<uk.co.bns.warehouse_api.dto.DpdServiceOption> options) {
        try {
            settingsService.set(LAST_KNOWN_SERVICES_KEY, objectMapper.writeValueAsString(options));
        } catch (Exception e) {
            log.warn("Failed to cache last-known DPD services: {}", e.getMessage());
        }
    }

    private List<uk.co.bns.warehouse_api.dto.DpdServiceOption> loadLastKnownServices() {
        String cached = settingsService.get(LAST_KNOWN_SERVICES_KEY, "");
        if (cached.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(cached,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, uk.co.bns.warehouse_api.dto.DpdServiceOption.class));
        } catch (Exception e) {
            log.warn("Failed to read cached DPD services: {}", e.getMessage());
            return List.of();
        }
    }

    private JsonNode fetchAvailableServices(Order order, String senderPostcode, String senderTown, String senderCountryCode) {
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode delivery = body.putObject("deliveryDetails").putObject("address");
        delivery.put("countryCode", order.getDeliveryCountryCode().toUpperCase());
        delivery.put("town", order.getDeliveryTown() != null ? order.getDeliveryTown() : "");
        delivery.put("postcode", order.getDeliveryPostcode());

        ObjectNode collection = body.putObject("collectionDetails").putObject("address");
        collection.put("countryCode", senderCountryCode);
        collection.put("town", senderTown);
        collection.put("postcode", senderPostcode);

        // Which services DPD offers depends on numberOfParcels as well as
        // weight - a heavy order asked for as a single parcel can tip DPD
        // into only offering its freight/pallet network, when the same
        // weight split realistically across the cartons it's actually
        // packed into (or will be) qualifies for ordinary multi-parcel
        // Parcel services instead. This was previously hardcoded to 1
        // parcel carrying the order's whole weight regardless of how many
        // cartons the order was packed into, which is why an order that's
        // normally sent as 2 everyday parcels was only ever being offered
        // (and then booked against) a single freight-tier service.
        List<BigDecimal> parcelWeights = parcelWeights(order);
        BigDecimal totalWeight = parcelWeights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        // Real per-carton weights aren't known yet at release time (before
        // the order's actually been packed), so the same order can still
        // legitimately get offered Freight here even once packed weight is
        // correctly split later - e.g. this order is a single ~35kg "parcel"
        // as far as this early lookup is concerned, and staff know from
        // experience it'll really go out as 2 ordinary parcels once packed.
        // Settings > DPD's "never offer Freight" weight cap addresses that:
        // only the weight sent to THIS availability check is capped -
        // whatever's actually declared on the real booked shipment at
        // despatch (buildRequestBody, below) always uses each carton's
        // genuine weight, untouched by this.
        body.put("totalWeight", applyServiceLookupWeightCap(totalWeight).doubleValue());
        body.put("shipmentType", 0); // Domestic
        body.put("numberOfParcels", parcelWeights.size());

        HttpRequest request = HttpRequest.newBuilder(URI.create(dpdAuthService.baseUrl() + "/v1/customer/shipping/reference/outboundservices"))
                .header("Authorization", "Bearer " + dpdAuthService.getAccessToken())
                .header("Client-Id", dpdAuthService.apiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        JsonNode responseBody = send(request);
        return responseBody.has("data") ? responseBody.get("data") : responseBody;
    }

    /**
     * One weight per parcel this order will actually ship as, in the same
     * terms buildRequestBody() itself uses: one entry per carton already
     * packed (its own entered weight, or worked out from its contents if
     * that's missing), or a single entry covering the whole order's weight
     * when packing hasn't happened yet. Shared by fetchAvailableServices()
     * (so the service lookup - and therefore booking - reflects the real
     * parcel split rather than always assuming one parcel) and available for
     * buildRequestBody() to stay consistent with it.
     */
    private List<BigDecimal> parcelWeights(Order order) {
        List<Carton> cartons = cartonRepository.findByOrder_IdOrderByCartonNumberAsc(order.getId());
        if (cartons.isEmpty()) {
            return List.of(totalWeightKg(order));
        }
        List<BigDecimal> weights = new java.util.ArrayList<>();
        for (Carton carton : cartons) {
            weights.add(carton.getWeightKg() != null ? carton.getWeightKg() : sumProductWeight(productQuantitiesForCarton(carton)));
        }
        return weights;
    }

    /**
     * Settings > DPD's "never offer Freight" cap - a deliberate, opt-in
     * override for the weight sent to DPD's own "what services are
     * available" check only, requested because that check can tip a heavy
     * order into only being offered a freight/pallet service, even for an
     * order staff know from experience will actually go out as ordinary
     * parcels once packed. Blank (the default) sends the real computed
     * weight unchanged - this only ever lowers it, never raises it, so it
     * can't accidentally offer a heavier-looking shipment than reality.
     */
    private BigDecimal applyServiceLookupWeightCap(BigDecimal weight) {
        String capSetting = settingsService.get("dpd_max_lookup_weight_kg", "").trim();
        if (capSetting.isBlank()) {
            return weight;
        }
        try {
            BigDecimal cap = new BigDecimal(capSetting);
            return weight.min(cap);
        } catch (NumberFormatException e) {
            log.warn("Ignoring invalid dpd_max_lookup_weight_kg setting '{}': {}", capSetting, e.getMessage());
            return weight;
        }
    }

    /**
     * Trims a raw response body for safe inclusion inside an exception
     * message/Bug Report - long enough to actually see the real shape of
     * DPD's response, short enough not to blow out logs or the bug-reports
     * table with a multi-megabyte label payload.
     */
    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "... (truncated)";
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
