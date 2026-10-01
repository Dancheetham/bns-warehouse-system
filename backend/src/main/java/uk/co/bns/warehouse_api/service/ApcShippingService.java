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
import uk.co.bns.warehouse_api.dto.ApcTrackingEvent;
import uk.co.bns.warehouse_api.dto.ApcTrackingResult;
import uk.co.bns.warehouse_api.dto.ServiceToggleOption;
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
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    // Settings > Couriers > APC > Available Services - a comma-separated list
    // of product codes the admin has unticked, so they stop appearing in the
    // order screen's Service dropdown without losing their place in the
    // cached/standard lists (so they can be ticked again later). Applied as a
    // filter right before returning options to the frontend, never before
    // caching - cacheLastKnownServices() always stores APC's full raw answer.
    private static final String DISABLED_SERVICES_KEY = "apc_disabled_services";

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

        JsonNode responseBody = postWithDimensionRetry("Orders.json", this::buildRequestBody, order, "book the APC shipment");
        // findPath digs into whichever wrapper the response actually uses
        // (e.g. {"Orders":{"Order":{...}}}, mirroring the request shape)
        // without needing to know the exact nesting up front - same
        // reasoning as the Messages.Code check in send().
        JsonNode orderNode = responseBody.findPath("Order");
        JsonNode data = !orderNode.isMissingNode() ? orderNode : responseBody;

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

        // Label isn't at Order.Label (that was the bug - always threw "APC
        // didn't return a label" regardless of whether APC actually sent
        // one) - it's nested under each shipment piece:
        // Order.ShipmentDetails.Items.Item.Label.Content, confirmed from the
        // guide's own literal JSON response example (section 4.2). Items can
        // be a single object (one piece) or an array (several, one Label
        // each - same "For Multi-Items" shape as the request body). Every
        // piece's label is collected and concatenated, same approach
        // DpdShippingService.joinLabelStrings already uses for a multi-
        // parcel DPD shipment - each is a self-contained ^XA...^XZ block, so
        // printing them back to back in one raw job is standard for ZPL.
        JsonNode orderNode = responseBody.findPath("Order");
        JsonNode data = !orderNode.isMissingNode() ? orderNode : responseBody;
        JsonNode itemsWrapper = data.findPath("Items");
        List<JsonNode> itemNodes = new java.util.ArrayList<>();
        JsonNode itemNode = itemsWrapper.path("Item");
        if (itemNode.isArray()) {
            itemNode.forEach(itemNodes::add);
        } else if (itemNode.isObject() && !itemNode.isMissingNode()) {
            itemNodes.add(itemNode);
        } else if (itemsWrapper.isArray()) {
            // Defensive fallback in case a future response ever comes back
            // with Items itself as a bare array rather than {"Item": ...} -
            // not expected per the guide, but cheap to handle.
            itemsWrapper.forEach(itemNodes::add);
        }

        java.io.ByteArrayOutputStream combined = new java.io.ByteArrayOutputStream();
        String format = null;
        for (JsonNode item : itemNodes) {
            JsonNode label = item.path("Label");
            String content = firstNonBlank(label, "Content", "content");
            if (content == null) continue;
            if (format == null) format = firstNonBlank(label, "Format", "format");
            try {
                combined.writeBytes(Base64.getDecoder().decode(content));
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("APC's label response couldn't be decoded: " + e.getMessage(), e);
            }
        }

        if (combined.size() == 0) {
            log.error("APC's label response for order {} had no Item.Label.Content anywhere: {}", order.getOrderNumber(), responseBody);
            throw new RuntimeException("APC didn't return a label for this waybill - not printed, to avoid sending garbage to the label printer");
        }
        return new ApcLabelResult(combined.toByteArray(), format != null ? format : "ZPL");
    }

    private static final DateTimeFormatter APC_TRACK_DATETIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    /**
     * Tracking, via APC's own authenticated Tracks.json (GET
     * Tracks/{waybill}.json?searchtype=CarrierWaybill&history=Yes) - unlike
     * DPD, which is just a public tracking-page link the frontend builds
     * itself (see dpdTrackingUrl), APC has no equivalent unauthenticated
     * consumer tracker confirmed to accept a Hypaship WayBill, so this is
     * surfaced inside the app instead of linked out to. Every scan across
     * every piece of the shipment is collected (a multi-carton order has one
     * Activity[] per Item) and sorted newest-first; events whose date/time
     * doesn't parse are still included, just sorted to the end, so a format
     * surprise in APC's response never hides scan history that did arrive.
     */
    public ApcTrackingResult trackShipment(Order order) {
        if (order.getApcWaybill() == null) {
            throw new ValidationException("Order " + order.getOrderNumber() + " has no APC shipment booked yet");
        }
        String url = apcAuthService.baseUrl() + "Tracks/" + order.getApcWaybill()
                + ".json?searchtype=CarrierWaybill&history=Yes";

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("remote-user", apcAuthService.authHeader())
                .header("Accept", "application/json")
                .GET()
                .build();

        JsonNode responseBody = send(request, "track the APC shipment");
        JsonNode trackNode = responseBody.findPath("Track");
        List<JsonNode> tracks = new java.util.ArrayList<>();
        if (trackNode.isArray()) {
            trackNode.forEach(tracks::add);
        } else if (trackNode.isObject() && !trackNode.isMissingNode()) {
            // A single result can come back as one object rather than a
            // one-item array, same quirk as ServiceAvailability's Service.
            tracks.add(trackNode);
        }

        List<ApcTrackingEvent> events = new java.util.ArrayList<>();
        for (JsonNode track : tracks) {
            JsonNode items = track.findPath("Items");
            List<JsonNode> itemNodes = new java.util.ArrayList<>();
            if (items.isArray()) {
                items.forEach(itemNodes::add);
            } else if (items.isObject() && !items.isMissingNode()) {
                itemNodes.add(items);
            }
            for (JsonNode itemWrapper : itemNodes) {
                JsonNode item = itemWrapper.has("Item") ? itemWrapper.get("Item") : itemWrapper;
                JsonNode activity = item.path("Activity");
                if (!activity.isArray()) continue;
                for (JsonNode activityEntry : activity) {
                    JsonNode statusNode = activityEntry.has("Status") ? activityEntry.get("Status") : activityEntry;
                    String description = firstNonBlank(statusNode, "StatusDescription", "statusDescription");
                    if (description == null) continue;
                    events.add(new ApcTrackingEvent(
                            firstNonBlank(statusNode, "StatusCode", "statusCode"),
                            description,
                            firstNonBlank(statusNode, "DateTime", "dateTime"),
                            firstNonBlank(statusNode, "Location", "location")));
                }
            }
        }

        events.sort((a, b) -> {
            LocalDateTime dtA = parseTrackDateTime(a.dateTime());
            LocalDateTime dtB = parseTrackDateTime(b.dateTime());
            if (dtA == null && dtB == null) return 0;
            if (dtA == null) return 1;
            if (dtB == null) return -1;
            return dtB.compareTo(dtA);
        });

        String latestStatus = events.isEmpty() ? null : events.get(0).description();
        String latestDateTime = events.isEmpty() ? null : events.get(0).dateTime();
        return new ApcTrackingResult(events, latestStatus, latestDateTime);
    }

    private LocalDateTime parseTrackDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value, APC_TRACK_DATETIME);
        } catch (Exception e) {
            return null;
        }
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
            JsonNode responseBody = postWithDimensionRetry(
                    "ServiceAvailability.json", this::buildServiceAvailabilityBody, order, "check APC service availability");
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
            return new ApcServiceLookupResult(excludeDisabled(options), true, null);
        } catch (Exception e) {
            log.warn("Live APC service availability check failed for order {}: {}", order.getOrderNumber(), e.getMessage());
            List<ApcServiceOption> cached = loadLastKnownServices();
            return new ApcServiceLookupResult(excludeDisabled(cached.isEmpty() ? STANDARD_SERVICES : cached), false, e.getMessage());
        }
    }

    /**
     * Settings > Couriers > APC > Available Services - every product code
     * this account has ever actually seen offered (the small built-in
     * STANDARD_SERVICES floor, plus whatever's accumulated in the live-
     * result cache), each marked with whether it's currently enabled. There's
     * no complete master catalog of every APC product code anywhere in this
     * codebase (see the integration guide's own table for that) - this page
     * can only toggle codes that have actually shown up for this account at
     * least once; a brand new code neither touches this list until a live
     * lookup returns it, at which point it's added here already enabled.
     */
    public List<ServiceToggleOption> listAllKnownServicesForToggle() {
        Set<String> disabled = disabledServiceCodes();
        Map<String, ApcServiceOption> byCode = new LinkedHashMap<>();
        for (ApcServiceOption o : STANDARD_SERVICES) byCode.put(o.code(), o);
        for (ApcServiceOption o : loadLastKnownServices()) byCode.put(o.code(), o);
        return byCode.values().stream()
                .map(o -> new ServiceToggleOption(o.code(), o.description(), !disabled.contains(o.code())))
                .toList();
    }

    public void setDisabledServices(Set<String> codes) {
        settingsService.set(DISABLED_SERVICES_KEY, String.join(",", codes));
    }

    private Set<String> disabledServiceCodes() {
        String raw = settingsService.get(DISABLED_SERVICES_KEY, "");
        if (raw.isBlank()) return Set.of();
        return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    private List<ApcServiceOption> excludeDisabled(List<ApcServiceOption> options) {
        Set<String> disabled = disabledServiceCodes();
        if (disabled.isEmpty()) return options;
        return options.stream().filter(o -> !disabled.contains(o.code())).toList();
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
        ObjectNode item = objectMapper.createObjectNode();
        item.put("Type", "ALL");
        item.put("Weight", weight.doubleValue());
        addDimensionsIfRequired(item);
        item.put("Value", customsValue(order).doubleValue());
        // "Items" is not itself an array - APC's schema is {"Items":{"Item": {...} } }
        // for a single item, or {"Items":{"Item": [ {...}, {...} ]}} for several
        // (the array nests one level inside "Item"; see buildRequestBody's
        // comment for the source in the integration guide).
        shipmentDetails.putObject("Items").set("Item", item);
        return wrapAsOrder(root);
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
        // DpdShippingService.buildRequestBody's per-carton parcels. No real
        // per-product/per-carton dimensions exist anywhere in this system
        // (Product/Carton only ever capture weight), and APC's own docs say
        // Length/Width/Height are only mandatory on some accounts - so
        // dimensions are only sent once this account is known to actually
        // need them (see addDimensionsIfRequired), rather than making up a
        // plausible-looking box size that skews APC's volumetric-weight
        // calculation and can bump a genuinely light item into a pricier
        // product tier. Falls back to a single piece covering the whole
        // order's weight when packing hasn't happened yet, exactly like
        // DPD's own fallback.
        List<Carton> cartons = cartonRepository.findByOrder_IdOrderByCartonNumberAsc(order.getId());
        ObjectNode shipmentDetails = root.putObject("ShipmentDetails");
        List<ObjectNode> items = new java.util.ArrayList<>();
        BigDecimal goodsValue = customsValue(order);

        if (!cartons.isEmpty()) {
            for (Carton carton : cartons) {
                BigDecimal weight = carton.getWeightKg() != null ? carton.getWeightKg() : totalWeightKg(order).divide(
                        BigDecimal.valueOf(cartons.size()), 3, java.math.RoundingMode.HALF_UP);
                items.add(buildItem(weight,
                        goodsValue.divide(BigDecimal.valueOf(cartons.size()), 2, java.math.RoundingMode.HALF_UP)));
            }
            shipmentDetails.put("NumberOfPieces", cartons.size());
        } else {
            items.add(buildItem(totalWeightKg(order), goodsValue));
            shipmentDetails.put("NumberOfPieces", 1);
        }

        // "Items" is not itself an array - per the integration guide's own
        // examples (single-item bookings show "Items":{"Item":{...}}; the
        // "For Multi-Items" callout shows "Items":{"Item":[{...},{...}]} -
        // the array nests one level inside "Item", not "Items" itself being
        // an array as this was previously built).
        ObjectNode itemsNode = shipmentDetails.putObject("Items");
        if (items.size() == 1) {
            itemsNode.set("Item", items.get(0));
        } else {
            ArrayNode itemArray = itemsNode.putArray("Item");
            items.forEach(itemArray::add);
        }

        ObjectNode goodsInfo = root.putObject("GoodsInfo");
        goodsInfo.put("GoodsDescription", settingsService.get("apc_goods_description", DEFAULT_GOODS_DESCRIPTION));
        goodsInfo.put("GoodsValue", goodsValue.doubleValue());
        goodsInfo.put("Fragile", false);
        goodsInfo.put("Security", false);
        goodsInfo.put("IncreasedLiability", "true".equals(settingsService.get("apc_increased_liability", "false")));

        return wrapAsOrder(root);
    }

    private ObjectNode buildItem(BigDecimal weight, BigDecimal value) {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("Weight", weight.doubleValue());
        addDimensionsIfRequired(item);
        item.put("Value", value.doubleValue());
        return item;
    }

    /**
     * APC's actual schema wants the whole order/availability payload wrapped
     * as {"Orders": {"Order": {...}}} - confirmed from the integration
     * guide's own literal JSON and XML request examples (both the booking
     * and ServiceAvailability calls use the same envelope). Everything built
     * above stays as a flat set of fields for readability; this is the one
     * place that wraps it into APC's required shape before it's sent.
     */
    private ObjectNode wrapAsOrder(ObjectNode order) {
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.putObject("Orders").set("Order", order);
        return wrapper;
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

    // Whether this APC account's own settings actually require
    // Item/Length/Width/Height on every call - the guide itself says this
    // "can be mandatory or optional depending on customer account settings"
    // (section 4, optional-fields table), so it can't be known up front.
    // Discovered automatically the first time it matters (see
    // postWithDimensionRetry) and remembered here from then on, so it's
    // only ever figured out once per account rather than on every call.
    private static final String DIMENSIONS_REQUIRED_KEY = "apc_dimensions_required";

    private boolean dimensionsRequired() {
        return "true".equals(settingsService.get(DIMENSIONS_REQUIRED_KEY, ""));
    }

    /**
     * Sends dimensions only once this account is known to need them -
     * before that's known, Length/Width/Height are omitted from every Item
     * entirely (see addDimensionsIfRequired). Sending them by default was
     * the previous behaviour, and it was actively wrong: a made-up default
     * parcel size (e.g. 30x20x20cm) makes APC calculate a volumetric weight
     * from those dimensions and bill/rate against whichever of actual vs
     * volumetric weight is higher - so a genuine 1kg MailPack was coming
     * out volumetric-rated at ~2kg purely from an invented box size, wrongly
     * pushing it toward a bigger, pricier product tier. Once this account is
     * confirmed to actually require the fields, 1cm is sent in each (not a
     * "plausible" box size) - enough to satisfy a mandatory-field check
     * without materially affecting volumetric weight.
     */
    private void addDimensionsIfRequired(ObjectNode item) {
        if (dimensionsRequired()) {
            item.put("Length", "1");
            item.put("Width", "1");
            item.put("Height", "1");
        }
    }

    private boolean looksLikeDimensionError(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase();
        return lower.contains("length") || lower.contains("width") || lower.contains("height") || lower.contains("dimension");
    }

    /**
     * POSTs a dimension-sensitive body (booking or service availability) and,
     * if APC rejects it in a way that looks like a missing-dimension
     * complaint and dimensions weren't sent, remembers that this account
     * needs them (DIMENSIONS_REQUIRED_KEY) and retries once with them
     * included. Any other failure (or a second failure after the retry) is
     * thrown as-is - this only ever adds one extra round trip, and only the
     * very first time it's needed for a given account.
     */
    private JsonNode postWithDimensionRetry(
            String endpoint, java.util.function.Function<Order, ObjectNode> bodyBuilder, Order order, String actionDescription) {
        ObjectNode body = bodyBuilder.apply(order);
        try {
            return send(buildPostRequest(endpoint, body), actionDescription);
        } catch (ValidationException e) {
            if (dimensionsRequired() || !looksLikeDimensionError(e.getMessage())) {
                throw e;
            }
            log.info("APC rejected a request without item dimensions ({}) - this account appears to require " +
                    "Length/Width/Height, so retrying with 1cm placeholder dimensions and remembering this for future calls.",
                    e.getMessage());
            settingsService.set(DIMENSIONS_REQUIRED_KEY, "true");
            ObjectNode retryBody = bodyBuilder.apply(order);
            return send(buildPostRequest(endpoint, retryBody), actionDescription);
        }
    }

    private HttpRequest buildPostRequest(String endpoint, ObjectNode body) {
        return HttpRequest.newBuilder(URI.create(apcAuthService.baseUrl() + endpoint))
                .header("remote-user", apcAuthService.authHeader())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
    }

    private JsonNode send(HttpRequest request, String actionDescription) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Failed to {} - APC returned {}: {}", actionDescription, response.statusCode(), response.body());
                throw new ValidationException("Failed to " + actionDescription + " - " + describeApcError(response.body(), response.statusCode()));
            }
            JsonNode parsed = objectMapper.readTree(response.body());
            // Every APC response envelope (ServiceAvailability/Orders/Tracks/
            // CancelOrder/...) carries its own Messages.Code/Description
            // nested under whichever wrapper key that endpoint uses - APC
            // can (and does) return HTTP 200 with a non-SUCCESS Messages.Code
            // (e.g. an auth or account problem), which previously sailed
            // through as a "successful" empty response - the actual cause of
            // an empty/"No services available" Service dropdown with no
            // error shown anywhere. findPath digs into whichever wrapper key
            // is present without needing to know it up front.
            JsonNode messages = parsed.findPath("Messages");
            if (!messages.isMissingNode()) {
                String code = messages.path("Code").asText("");
                if (!code.isBlank() && !"SUCCESS".equalsIgnoreCase(code)) {
                    String description = messages.path("Description").asText(code);
                    log.error("APC rejected the request to {} - Messages.Code={}: {}", actionDescription, code, description);
                    throw new ValidationException("Failed to " + actionDescription + " - APC said: " + description);
                }
            }
            return parsed;
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
