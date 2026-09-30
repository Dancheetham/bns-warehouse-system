package uk.co.bns.warehouse_api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.co.bns.warehouse_api.dto.ApcLabelResult;
import uk.co.bns.warehouse_api.dto.ApcOrderResult;
import uk.co.bns.warehouse_api.dto.ApcServiceLookupResult;
import uk.co.bns.warehouse_api.dto.ApcServiceOption;
import uk.co.bns.warehouse_api.entity.Carton;
import uk.co.bns.warehouse_api.entity.Order;
import uk.co.bns.warehouse_api.entity.OrderLine;
import uk.co.bns.warehouse_api.exception.ValidationException;
import uk.co.bns.warehouse_api.repository.CartonRepository;
import uk.co.bns.warehouse_api.repository.OrderRepository;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

/**
 * Books shipments through APC Overnight's Hypaship Booking Platform API v3
 * (JSON throughout, not the XML variant, for consistency with the rest of
 * this codebase) - the APC equivalent of DpdShippingService, deliberately
 * mirroring its structure (validate -> build -> POST -> save result fields
 * on Order -> return a small result record; NOT @Transactional, for exactly
 * the same "don't let a courier failure roll back despatch" reason
 * documented on DpdShippingService.createShipment).
 *
 * Unlike DPD, APC's auth is a plain recomputed-per-request header
 * (ApcAuthService.authHeader()), and this first version deliberately covers
 * only Orders.json (book) and the label fetch - Tracking/Amend/Cancel/
 * non-GB/Safeplace are out of scope, matching what DPD parity actually
 * needs.
 */
@Service
@RequiredArgsConstructor
public class ApcShippingService {

    private static final Logger log = LoggerFactory.getLogger(ApcShippingService.class);

    static final String DEFAULT_GOODS_DESCRIPTION = "Telecoms and networking equipment";

    // No per-product/per-carton dimensions exist anywhere in this system
    // (Product/Carton only ever capture weight) - APC's Item block requires
    // Length/Width/Height regardless, so a configurable default parcel size
    // is used for every item rather than inventing per-product dimensions
    // that don't exist. See Settings > Couriers > APC.
    private static final String DEFAULT_PARCEL_LENGTH_CM = "30";
    private static final String DEFAULT_PARCEL_WIDTH_CM = "20";
    private static final String DEFAULT_PARCEL_HEIGHT_CM = "20";

    // Last-resort fallback if APC has never once returned a live service list
    // for this account (freshly configured, or Training/Live both
    // unreachable) - see checkServiceAvailability()/loadLastKnownServices().
    // Deliberately small and generic; the live lookup and the cache above it
    // are what actually drive the dropdown day to day.
    public static final List<ApcServiceOption> STANDARD_SERVICES = List.of(
            new ApcServiceOption("ND10", "Next Day by 10:00"),
            new ApcServiceOption("ND12", "Next Day by 12:00"),
            new ApcServiceOption("ND16", "Next Day by 16:00 (standard)"),
            new ApcServiceOption("NDSAT", "Next Day Saturday"),
            new ApcServiceOption("ECO48", "Economy (2-3 day)")
    );

    // Where the last successfully-fetched live service list is cached (as
    // JSON), so the dropdown still has real, previously-offered options when
    // a later live lookup fails - mirrors DPD_LAST_KNOWN_SERVICES_KEY in
    // DpdShippingService exactly.
    private static final String LAST_KNOWN_SERVICES_KEY = "apc_last_known_services";

    private final ApcAuthService apcAuthService;
    private final SettingsService settingsService;
    private final OrderRepository orderRepository;
    private final CartonRepository cartonRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private static final DateTimeFormatter APC_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Deliberately NOT @Transactional - see the class-level comment and
    // DpdShippingService.createShipment for why: this must never be able to
    // mark a shared DespatchService transaction rollback-only when called
    // from bookApcShipment() at despatch time.
    public ApcOrderResult createOrder(Order order) {
        validateOrder(order);

        ObjectNode body = buildRequestBody(order);

        HttpRequest request = HttpRequest.newBuilder(URI.create(apcAuthService.baseUrl() + "Orders.json"))
                .header("remote-user", apcAuthService.authHeader())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        JsonNode responseBody = send(request, "book the APC shipment");
        JsonNode data = responseBody.has("Order") ? responseBody.get("Order") : responseBody;

        String orderNumber = firstNonBlank(data, "OrderNumber", "orderNumber");
        String waybill = firstNonBlank(data, "WayBill", "Waybill", "waybill");
        if (waybill == null) {
            log.error("APC accepted the order for {} but no WayBill came back: {}", order.getOrderNumber(), responseBody);
            throw new RuntimeException("APC accepted the order but didn't return a waybill - check My APC before retrying");
        }

        order.setApcOrderNumber(orderNumber);
        order.setApcWaybill(waybill);
        order.setApcShippedAt(LocalDateTime.now());
        orderRepository.save(order);

        log.info("Booked APC shipment for order {}: orderNumber={} waybill={}", order.getOrderNumber(), orderNumber, waybill);
        return new ApcOrderResult(orderNumber, waybill);
    }

    /**
     * Fetches the label for an already-booked order, live/on-demand rather
     * than pre-fetching a short delay after booking (APC's own docs suggest
     * either) - simpler, and consistent with how DPD's labels are fetched
     * live on every "Print Label" click rather than stored anywhere.
     * Requests ZPL so it can go through the same printRaw()/print-agent flow
     * DPD's labels already use.
     */
    public ApcLabelResult getLabel(Order order) {
        if (order.getApcWaybill() == null) {
            throw new ValidationException("Order " + order.getOrderNumber() + " has no APC shipment booked yet");
        }
        String url = apcAuthService.baseUrl() + "Orders/" + order.getApcWaybill()
                + ".json?searchtype=CarrierWaybill&labelformat=ZPL&labels=True";

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("remote-user", apcAuthService.authHeader())
                .header("Accept", "application/json")
                .GET()
                .build();

        JsonNode responseBody = send(request, "fetch the APC label");
        JsonNode data = responseBody.has("Order") ? responseBody.get("Order") : responseBody;
        JsonNode label = data.has("Label") ? data.get("Label") : data.path("Labels").isArray() && data.path("Labels").size() > 0
                ? data.path("Labels").get(0) : data.path("Label");

        String content = firstNonBlank(label, "Content", "content");
        String format = firstNonBlank(label, "Format", "format");
        if (content == null) {
            log.error("APC's label response for order {} had no Label.Content: {}", order.getOrderNumber(), responseBody);
            throw new RuntimeException("APC didn't return a label for this waybill - not printed, to avoid sending garbage to the label printer");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(content);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("APC's label response couldn't be decoded: " + e.getMessage(), e);
        }
        return new ApcLabelResult(decoded, format != null ? format : "ZPL");
    }

    /**
     * The services APC actually has available right now for this order's
     * delivery address and item weight/size - via ServiceAvailability.json
     * with Item/Type=ALL, so the response is already correctly filtered to
     * (for example) MailPack + CourierPack + Parcel for a sub-1kg item, or
     * just Parcel for anything over CourierPack's 5kg cap - same weight
     * rules APC's own Hypaship website applies (see the Service Product
     * Codes table: MailPack max 1kg, CourierPack max 5kg, Standard Parcel
     * max 30kg). Mirrors DpdShippingService.listAvailableServices(order)
     * exactly: falls back to the last successfully-fetched list (cached in
     * Settings) if the live call fails, and further to STANDARD_SERVICES if
     * nothing's ever been cached.
     */
    public ApcServiceLookupResult checkServiceAvailability(Order order) {
        if (order.getDeliveryPostcode() == null || order.getDeliveryPostcode().isBlank()
                || order.getDeliveryCountryCode() == null || order.getDeliveryCountryCode().isBlank()) {
            throw new ValidationException("This order needs a delivery postcode and country before APC services can be looked up");
        }
        try {
            ObjectNode body = buildServiceAvailabilityBody(order);
            HttpRequest request = HttpRequest.newBuilder(URI.create(apcAuthService.baseUrl() + "ServiceAvailability.json"))
                    .header("remote-user", apcAuthService.authHeader())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            JsonNode responseBody = send(request, "check APC service availability");
            JsonNode serviceList = responseBody.path("ServiceAvailability").path("Services").path("Service");
            List<ApcServiceOption> options = new java.util.ArrayList<>();
            if (serviceList.isArray()) {
                for (JsonNode service : serviceList) {
                    String code = firstNonBlank(service, "ProductCode", "productCode");
                    String name = firstNonBlank(service, "ServiceName", "serviceName");
                    if (code == null) continue;
                    options.add(new ApcServiceOption(code, name != null ? name : code));
                }
            } else if (serviceList.isObject() && !serviceList.isMissingNode()) {
                // A single result comes back as one object rather than a one-item array.
                String code = firstNonBlank(serviceList, "ProductCode", "productCode");
                String name = firstNonBlank(serviceList, "ServiceName", "serviceName");
                if (code != null) {
                    options.add(new ApcServiceOption(code, name != null ? name : code));
                }
            }
            if (!options.isEmpty()) {
                cacheLastKnownServices(options);
            }
            return new ApcServiceLookupResult(options, true, null);
        } catch (Exception e) {
            log.warn("Live APC service availability check failed for order {}: {}", order.getOrderNumber(), e.getMessage());
            List<ApcServiceOption> cached = loadLastKnownServices();
            return new ApcServiceLookupResult(cached.isEmpty() ? STANDARD_SERVICES : cached, false, e.getMessage());
        }
    }

    /**
     * Same shape as buildRequestBody's Delivery/Collection/GoodsInfo/Items
     * blocks, minus CollectionDate's role in actually booking anything -
     * ServiceAvailability.json needs the same fields to know what's
     * deliverable to this address at this weight, but Item/Type=ALL so APC
     * returns every product family that fits rather than validating one.
     */
    private ObjectNode buildServiceAvailabilityBody(Order order) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("CollectionDate", LocalDate.now().format(APC_DATE));
        root.put("ReadyAt", settingsService.get("apc_ready_at", "09:00"));
        root.put("ClosedAt", settingsService.get("apc_closed_at", "17:00"));

        String collectionPostcode = settingsService.get("apc_collection_postcode", "");
        if (!collectionPostcode.isBlank()) {
            ObjectNode collection = root.putObject("Collection");
            collection.put("PostalCode", collectionPostcode);
            collection.put("CountryCode", settingsService.get("apc_collection_country_code", "GB"));
        }

        ObjectNode delivery = root.putObject("Delivery");
        delivery.put("PostalCode", order.getDeliveryPostcode());
        delivery.put("CountryCode", order.getDeliveryCountryCode().toUpperCase());

        ObjectNode goodsInfo = root.putObject("GoodsInfo");
        goodsInfo.put("GoodsValue", customsValue(order).doubleValue());
        goodsInfo.put("Fragile", false);

        List<Carton> cartons = cartonRepository.findByOrder_IdOrderByCartonNumberAsc(order.getId());
        BigDecimal weight = cartons.isEmpty() ? totalWeightKg(order) : cartons.stream()
                .map(c -> c.getWeightKg() != null ? c.getWeightKg() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (weight.compareTo(BigDecimal.ZERO) <= 0) {
            // A zero/unset weight would make APC reject the call outright
            // (and every product tier would trivially "fit" it, which isn't
            // useful) - 1g is enough to get a real, weight-aware answer
            // without claiming the order actually weighs nothing.
            weight = new BigDecimal("0.01");
        }

        ObjectNode shipmentDetails = root.putObject("ShipmentDetails");
        shipmentDetails.put("NumberOfPieces", Math.max(cartons.size(), 1));
        ArrayNode items = shipmentDetails.putArray("Items");
        ObjectNode item = items.addObject();
        item.put("Type", "ALL");
        item.put("Weight", weight.doubleValue());
        item.put("Length", settingsService.get("apc_default_parcel_length_cm", DEFAULT_PARCEL_LENGTH_CM));
        item.put("Width", settingsService.get("apc_default_parcel_width_cm", DEFAULT_PARCEL_WIDTH_CM));
        item.put("Height", settingsService.get("apc_default_parcel_height_cm", DEFAULT_PARCEL_HEIGHT_CM));
        item.put("Value", customsValue(order).doubleValue());
        return root;
    }

    private void cacheLastKnownServices(List<ApcServiceOption> options) {
        try {
            settingsService.set(LAST_KNOWN_SERVICES_KEY, objectMapper.writeValueAsString(options));
        } catch (Exception e) {
            log.warn("Failed to cache last-known APC services: {}", e.getMessage());
        }
    }

    private List<ApcServiceOption> loadLastKnownServices() {
        String cached = settingsService.get(LAST_KNOWN_SERVICES_KEY, "");
        if (cached.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(cached,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ApcServiceOption.class));
        } catch (Exception e) {
            log.warn("Failed to read cached APC services: {}", e.getMessage());
            return List.of();
        }
    }

    private void validateOrder(Order order) {
        if (order.getDeliveryAddressLine1() == null || order.getDeliveryAddressLine1().isBlank()) {
            throw new ValidationException("Order " + order.getOrderNumber() + " has no delivery address line 1 set - add one before booking an APC shipment");
        }
        if (order.getDeliveryPostcode() == null || order.getDeliveryPostcode().isBlank()) {
            throw new ValidationException("Order " + order.getOrderNumber() + " has no delivery postcode set");
        }
        if (order.getDeliveryCountryCode() == null || order.getDeliveryCountryCode().isBlank()) {
            throw new ValidationException("Order " + order.getOrderNumber() + " has no delivery country code set");
        }
        if (order.getDeliveryTown() == null || order.getDeliveryTown().isBlank()) {
            throw new ValidationException("Order " + order.getOrderNumber() + " has no delivery town/city set - APC requires one on every order");
        }
    }

    private ObjectNode buildRequestBody(Order order) {
        ObjectNode root = objectMapper.createObjectNode();

        root.put("CollectionDate", LocalDate.now().format(APC_DATE));
        root.put("ReadyAt", settingsService.get("apc_ready_at", "09:00"));
        root.put("ClosedAt", settingsService.get("apc_closed_at", "17:00"));

        String serviceCode = order.getApcServiceCode() != null && !order.getApcServiceCode().isBlank()
                ? order.getApcServiceCode() : settingsService.get("apc_default_product_code", "");
        if (!serviceCode.isBlank()) {
            root.put("ProductCode", serviceCode);
        }

        root.put("Reference", order.getOrderNumber());

        // Collection is deliberately omitted unless every override field is
        // filled in under Settings > Couriers > APC - omitting it entirely
        // makes APC use the account's own default operational
        // address/depot, which is what BNS wants by default (BNS is the
        // collection point, not the customer, on every domestic order).
        String collectionOrganisation = settingsService.get("apc_collection_organisation", "");
        String collectionStreet = settingsService.get("apc_collection_street", "");
        String collectionPostcode = settingsService.get("apc_collection_postcode", "");
        String collectionCity = settingsService.get("apc_collection_city", "");
        if (!collectionOrganisation.isBlank() && !collectionStreet.isBlank()
                && !collectionPostcode.isBlank() && !collectionCity.isBlank()) {
            ObjectNode collection = root.putObject("Collection");
            collection.put("CompanyName", collectionOrganisation);
            collection.put("AddressLine1", collectionStreet);
            collection.put("PostalCode", collectionPostcode);
            collection.put("City", collectionCity);
            collection.put("CountryCode", settingsService.get("apc_collection_country_code", "GB"));
            String collectionContactName = settingsService.get("apc_collection_contact_name", "");
            String collectionContactPhone = settingsService.get("apc_collection_contact_phone", "");
            if (!collectionContactName.isBlank() || !collectionContactPhone.isBlank()) {
                ObjectNode contact = collection.putObject("Contact");
                if (!collectionContactName.isBlank()) contact.put("Name", collectionContactName);
                if (!collectionContactPhone.isBlank()) contact.put("Telephone", collectionContactPhone);
            }
        }

        ObjectNode delivery = root.putObject("Delivery");
        delivery.put("CompanyName", order.getDeliveryName() != null ? order.getDeliveryName() : order.getCustomerName());
        delivery.put("AddressLine1", order.getDeliveryAddressLine1());
        if (order.getDeliveryAddressLine2() != null && !order.getDeliveryAddressLine2().isBlank()) {
            delivery.put("AddressLine2", order.getDeliveryAddressLine2());
        }
        delivery.put("PostalCode", order.getDeliveryPostcode());
        delivery.put("City", order.getDeliveryTown());
        delivery.put("CountryCode", order.getDeliveryCountryCode().toUpperCase());
        if ((order.getDeliveryPhone() != null && !order.getDeliveryPhone().isBlank())
                || (order.getCustomerEmail() != null && !order.getCustomerEmail().isBlank())) {
            ObjectNode contact = delivery.putObject("Contact");
            if (order.getDeliveryName() != null && !order.getDeliveryName().isBlank()) {
                contact.put("Name", order.getDeliveryName());
            }
            if (order.getDeliveryPhone() != null && !order.getDeliveryPhone().isBlank()) {
                contact.put("Telephone", order.getDeliveryPhone());
            }
            if (order.getCustomerEmail() != null && !order.getCustomerEmail().isBlank()) {
                contact.put("Email", order.getCustomerEmail());
            }
        }

        // One APC "piece" per physical carton already packed - same idea as
        // DpdShippingService.buildRequestBody's per-carton parcels, but
        // without per-product dimensions to draw on (see the class-level
        // comment), so every piece uses the configured default parcel size.
        // Falls back to a single piece covering the whole order's weight
        // when packing hasn't happened yet, exactly like DPD's own fallback.
        List<Carton> cartons = cartonRepository.findByOrder_IdOrderByCartonNumberAsc(order.getId());
        ObjectNode shipmentDetails = root.putObject("ShipmentDetails");
        ArrayNode items = shipmentDetails.putArray("Items");
        BigDecimal goodsValue = customsValue(order);

        String lengthCm = settingsService.get("apc_default_parcel_length_cm", DEFAULT_PARCEL_LENGTH_CM);
        String widthCm = settingsService.get("apc_default_parcel_width_cm", DEFAULT_PARCEL_WIDTH_CM);
        String heightCm = settingsService.get("apc_default_parcel_height_cm", DEFAULT_PARCEL_HEIGHT_CM);

        if (!cartons.isEmpty()) {
            for (Carton carton : cartons) {
                BigDecimal weight = carton.getWeightKg() != null ? carton.getWeightKg() : totalWeightKg(order).divide(
                        BigDecimal.valueOf(cartons.size()), 3, java.math.RoundingMode.HALF_UP);
                addItem(items, weight, lengthCm, widthCm, heightCm,
                        goodsValue.divide(BigDecimal.valueOf(cartons.size()), 2, java.math.RoundingMode.HALF_UP));
            }
            shipmentDetails.put("NumberOfPieces", cartons.size());
        } else {
            addItem(items, totalWeightKg(order), lengthCm, widthCm, heightCm, goodsValue);
            shipmentDetails.put("NumberOfPieces", 1);
        }

        ObjectNode goodsInfo = root.putObject("GoodsInfo");
        goodsInfo.put("GoodsDescription", settingsService.get("apc_goods_description", DEFAULT_GOODS_DESCRIPTION));
        goodsInfo.put("GoodsValue", goodsValue.doubleValue());
        goodsInfo.put("Fragile", false);
        goodsInfo.put("Security", false);
        goodsInfo.put("IncreasedLiability", "true".equals(settingsService.get("apc_increased_liability", "false")));

        return root;
    }

    private void addItem(ArrayNode items, BigDecimal weight, String lengthCm, String widthCm, String heightCm, BigDecimal value) {
        ObjectNode item = items.addObject();
        item.put("Weight", weight.doubleValue());
        item.put("Length", lengthCm);
        item.put("Width", widthCm);
        item.put("Height", heightCm);
        item.put("Value", value.doubleValue());
    }

    private BigDecimal customsValue(Order order) {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderLine line : order.getLines()) {
            if (line.getUnitPrice() == null) continue;
            total = total.add(line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantityOrdered())));
        }
        return total;
    }

    private BigDecimal totalWeightKg(Order order) {
        return order.getLines().stream()
                .map(l -> (l.getProduct().getWeightKg() != null ? l.getProduct().getWeightKg() : BigDecimal.ZERO)
                        .multiply(BigDecimal.valueOf(l.getQuantityOrdered())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String firstNonBlank(JsonNode node, String... fieldNames) {
        for (String field : fieldNames) {
            String value = node.path(field).asText(null);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private JsonNode send(HttpRequest request, String actionDescription) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return objectMapper.readTree(response.body());
            }
            log.error("Failed to {} - APC returned {}: {}", actionDescription, response.statusCode(), response.body());
            throw new ValidationException("Failed to " + actionDescription + " - " + describeApcError(response.body(), response.statusCode()));
        } catch (java.io.IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Failed to " + actionDescription + ": " + e.getMessage(), e);
        }
    }

    /**
     * APC's error responses aren't as consistently structured as DPD's - the
     * raw body (truncated) is surfaced directly when no obvious
     * message/error field is found, rather than guessing at a shape that
     * might not match.
     */
    private String describeApcError(String responseBody, int statusCode) {
        try {
            JsonNode parsed = objectMapper.readTree(responseBody);
            String message = firstNonBlank(parsed, "Message", "message", "error", "Error");
            if (message != null) {
                return "APC said: " + message + " - check the order details and Settings > Couriers > APC";
            }
        } catch (Exception ignored) {
            // fall through to the raw body below
        }
        return "APC returned HTTP " + statusCode + ": " + truncate(responseBody, 500);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "... (truncated)";
    }
}
