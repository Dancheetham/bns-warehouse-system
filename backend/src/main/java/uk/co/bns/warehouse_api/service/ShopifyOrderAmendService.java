package uk.co.bns.warehouse_api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.co.bns.warehouse_api.entity.Order;
import uk.co.bns.warehouse_api.entity.OrderLine;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pushes an order edit made here back to the underlying Shopify order, for
 * orders that originated from Shopify - so an address, quantity or product
 * change made in the warehouse system actually shows up on the customer's
 * Shopify order page too, not just here. Best-effort and non-blocking, same
 * philosophy as ShopifyFulfillmentService: a save in this system must never
 * be held up or reversed by Shopify being unreachable or rejecting the edit.
 *
 * Two entirely separate Shopify mechanisms are involved:
 * - Address changes go through the plain `orderUpdate` mutation.
 * - Product/quantity changes go through Shopify's "order edit" session
 *   (orderEditBegin -> one or more edit mutations -> orderEditCommit) -
 *   the only way to change what's on an order after it's placed.
 *
 * Important limitation, by Shopify's own design rather than a gap here:
 * order edits can only LOWER a line's price (via a discount) - there is no
 * API path to raise a line above its original variant price. A price
 * increase is deliberately never attempted automatically; it's reported
 * back so staff know to handle it on Shopify directly rather than risking
 * silently wrong money on a customer's order.
 */
@Service
@RequiredArgsConstructor
public class ShopifyOrderAmendService {

    private static final Logger log = LoggerFactory.getLogger(ShopifyOrderAmendService.class);

    private final ObjectMapper objectMapper;
    private final SettingsService settingsService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${shopify.shop-domain:}")
    private String shopDomain;

    @Value("${shopify.api-version:2025-01}")
    private String apiVersion;

    /** A line's state at snapshot time, keyed by SKU so it survives the line's local row being replaced on save. */
    public record LineSnapshot(int quantity, BigDecimal unitPrice) {}

    /** The order's amendable state, captured before an edit is applied, so it can be diffed against the saved state afterwards. */
    public record Snapshot(
            String deliveryName, String addressLine1, String addressLine2, String town,
            String postcode, String countryCode, String phone,
            Map<String, LineSnapshot> linesBySku
    ) {}

    public Snapshot snapshot(Order order) {
        Map<String, LineSnapshot> lines = new LinkedHashMap<>();
        for (OrderLine line : order.getLines()) {
            if (line.getProduct() == null || line.getProduct().getSku() == null) continue;
            lines.put(line.getProduct().getSku(), new LineSnapshot(line.getQuantityOrdered(), line.getUnitPrice()));
        }
        return new Snapshot(order.getDeliveryName(), order.getDeliveryAddressLine1(), order.getDeliveryAddressLine2(),
                order.getDeliveryTown(), order.getDeliveryPostcode(), order.getDeliveryCountryCode(),
                order.getDeliveryPhone(), lines);
    }

    /**
     * Best-effort, never throws - diffs `before` against the order's current
     * (just-saved) state and pushes whatever changed to Shopify. Returns a
     * short status string for the save response, or null if there was
     * nothing to push (not a Shopify order, Shopify not connected, or
     * genuinely nothing changed).
     */
    public String syncAmendments(Order order, Snapshot before) {
        if (order.getShopifyOrderId() == null) return null;
        String accessToken = settingsService.get(ShopifyOAuthService.ACCESS_TOKEN_KEY, "");
        if (shopDomain.isBlank() || accessToken.isBlank()) return null;

        Snapshot after = snapshot(order);
        List<String> results = new ArrayList<>();

        if (addressChanged(before, after)) {
            try {
                results.add(syncAddress(order, accessToken));
            } catch (Exception e) {
                log.warn("Shopify address sync failed for order {}: {}", order.getOrderNumber(), e.getMessage());
                results.add("Address change not pushed to Shopify: " + e.getMessage());
            }
        }

        try {
            String lineResult = syncLineItems(order, before, after, accessToken);
            if (lineResult != null) results.add(lineResult);
        } catch (Exception e) {
            log.warn("Shopify line item sync failed for order {}: {}", order.getOrderNumber(), e.getMessage());
            results.add("Product/quantity changes not pushed to Shopify: " + e.getMessage());
        }

        return results.isEmpty() ? null : String.join(" ", results);
    }

    private boolean addressChanged(Snapshot before, Snapshot after) {
        return !Objects.equals(before.deliveryName(), after.deliveryName())
                || !Objects.equals(before.addressLine1(), after.addressLine1())
                || !Objects.equals(before.addressLine2(), after.addressLine2())
                || !Objects.equals(before.town(), after.town())
                || !Objects.equals(before.postcode(), after.postcode())
                || !Objects.equals(before.countryCode(), after.countryCode())
                || !Objects.equals(before.phone(), after.phone());
    }

    private String syncAddress(Order order, String accessToken) throws Exception {
        String fullName = order.getDeliveryName() != null ? order.getDeliveryName().trim() : "";
        int spaceIdx = fullName.indexOf(' ');
        String firstName = spaceIdx > 0 ? fullName.substring(0, spaceIdx) : fullName;
        String lastName = spaceIdx > 0 ? fullName.substring(spaceIdx + 1) : "";

        Map<String, Object> address = new LinkedHashMap<>();
        address.put("firstName", firstName.isBlank() ? null : firstName);
        address.put("lastName", lastName.isBlank() ? null : lastName);
        address.put("address1", order.getDeliveryAddressLine1());
        address.put("address2", order.getDeliveryAddressLine2());
        address.put("city", order.getDeliveryTown());
        address.put("zip", order.getDeliveryPostcode());
        address.put("countryCode", order.getDeliveryCountryCode() != null ? order.getDeliveryCountryCode().toUpperCase() : null);
        address.put("phone", order.getDeliveryPhone());

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("id", order.getShopifyOrderId());
        input.put("shippingAddress", address);

        JsonNode result = graphql(ORDER_UPDATE_MUTATION, Map.of("input", input), accessToken);
        JsonNode userErrors = result.path("data").path("orderUpdate").path("userErrors");
        if (userErrors.size() > 0) {
            return "Shopify rejected the address update: " + userErrors.get(0).path("message").asText();
        }
        if (result.has("errors")) {
            return "Shopify GraphQL error updating the address";
        }
        return "Address updated on Shopify.";
    }

    private String syncLineItems(Order order, Snapshot before, Snapshot after, String accessToken) throws Exception {
        List<String> skusToRemove = new ArrayList<>();
        List<String> skusToAdd = new ArrayList<>();
        Map<String, Integer> skusToSetQuantity = new LinkedHashMap<>();
        // Price changes are detected and reported so staff know to go and
        // update Shopify directly, but neither direction is pushed
        // automatically yet: a rise genuinely can't be (Shopify's order-edit
        // API can only ever lower a line, via a discount, never raise one
        // above its original variant price), and a fall - while technically
        // possible via that same discount mechanism - isn't implemented in
        // this release. It touches customer-facing order totals directly and
        // this feature hasn't been exercised against a live Shopify store
        // yet, so it's deliberately left as a flagged manual step for now
        // rather than risking an untested money mutation.
        List<String> priceIncreasesSkipped = new ArrayList<>();
        List<String> priceDecreasesSkipped = new ArrayList<>();

        for (String sku : before.linesBySku().keySet()) {
            LineSnapshot b = before.linesBySku().get(sku);
            LineSnapshot a = after.linesBySku().get(sku);
            if (a == null) {
                skusToRemove.add(sku);
            } else if (a.quantity() != b.quantity()) {
                skusToSetQuantity.put(sku, a.quantity());
            }
            if (a != null && b.unitPrice() != null && a.unitPrice() != null) {
                int cmp = a.unitPrice().compareTo(b.unitPrice());
                if (cmp > 0) {
                    priceIncreasesSkipped.add(sku);
                } else if (cmp < 0) {
                    priceDecreasesSkipped.add(sku);
                }
            }
        }
        for (String sku : after.linesBySku().keySet()) {
            if (!before.linesBySku().containsKey(sku)) {
                skusToAdd.add(sku);
            }
        }

        if (skusToRemove.isEmpty() && skusToAdd.isEmpty() && skusToSetQuantity.isEmpty()) {
            return buildLineSyncMessage(List.of(), List.of(), priceIncreasesSkipped, priceDecreasesSkipped);
        }

        // Map SKU -> the product for each new line, so we have its Shopify
        // variant ID to hand to orderEditAddVariant.
        Map<String, uk.co.bns.warehouse_api.entity.Product> productsBySku = new LinkedHashMap<>();
        for (OrderLine line : order.getLines()) {
            if (line.getProduct() != null && line.getProduct().getSku() != null) {
                productsBySku.put(line.getProduct().getSku(), line.getProduct());
            }
        }

        JsonNode beginResult = graphql(ORDER_EDIT_BEGIN_MUTATION, Map.of("id", order.getShopifyOrderId()), accessToken);
        JsonNode beginErrors = beginResult.path("data").path("orderEditBegin").path("userErrors");
        if (beginErrors.size() > 0) {
            return "Couldn't start a Shopify order edit: " + beginErrors.get(0).path("message").asText();
        }
        String calculatedOrderId = beginResult.path("data").path("orderEditBegin").path("calculatedOrder").path("id").asText(null);
        if (calculatedOrderId == null) {
            return "Couldn't start a Shopify order edit - no calculated order returned";
        }

        // Existing calculated line items, matched by SKU (same approach as
        // ShopifyFulfillmentService) so we don't need to separately track
        // Shopify's own line item IDs locally.
        Map<String, String> calculatedLineItemIdBySku = new LinkedHashMap<>();
        for (JsonNode li : beginResult.path("data").path("orderEditBegin").path("calculatedOrder")
                .path("lineItems").path("nodes")) {
            String sku = li.path("lineItem").path("sku").asText(null);
            if (sku != null) {
                calculatedLineItemIdBySku.put(sku, li.path("id").asText());
            }
        }

        List<String> notPushed = new ArrayList<>();
        List<String> pushed = new ArrayList<>();

        for (String sku : skusToRemove) {
            String lineItemId = calculatedLineItemIdBySku.get(sku);
            if (lineItemId == null) {
                notPushed.add(sku + " (not found on the Shopify order - may already be fully fulfilled there)");
                continue;
            }
            JsonNode r = graphql(ORDER_EDIT_SET_QUANTITY_MUTATION,
                    Map.of("id", calculatedOrderId, "lineItemId", lineItemId, "quantity", 0), accessToken);
            if (hasEditErrors(r, "orderEditSetQuantity", notPushed, sku)) continue;
            pushed.add("removed " + sku);
        }

        for (Map.Entry<String, Integer> entry : skusToSetQuantity.entrySet()) {
            String lineItemId = calculatedLineItemIdBySku.get(entry.getKey());
            if (lineItemId == null) {
                notPushed.add(entry.getKey() + " (not found on the Shopify order - may already be fully fulfilled there)");
                continue;
            }
            JsonNode r = graphql(ORDER_EDIT_SET_QUANTITY_MUTATION,
                    Map.of("id", calculatedOrderId, "lineItemId", lineItemId, "quantity", entry.getValue()), accessToken);
            if (hasEditErrors(r, "orderEditSetQuantity", notPushed, entry.getKey())) continue;
            pushed.add(entry.getKey() + " -> qty " + entry.getValue());
        }

        for (String sku : skusToAdd) {
            uk.co.bns.warehouse_api.entity.Product product = productsBySku.get(sku);
            String variantId = product != null ? product.getShopifyVariantId() : null;
            int quantity = after.linesBySku().get(sku).quantity();
            if (variantId == null) {
                notPushed.add(sku + " (no Shopify variant on record for this product - sync it from Shopify first)");
                continue;
            }
            JsonNode r = graphql(ORDER_EDIT_ADD_VARIANT_MUTATION,
                    Map.of("id", calculatedOrderId, "variantId", variantId, "quantity", quantity), accessToken);
            if (hasEditErrors(r, "orderEditAddVariant", notPushed, sku)) continue;
            pushed.add("added " + sku + " x" + quantity);
        }

        if (pushed.isEmpty()) {
            // Nothing actually went through - no point committing an empty edit.
            return buildLineSyncMessage(pushed, notPushed, priceIncreasesSkipped, priceDecreasesSkipped);
        }

        JsonNode commitResult = graphql(ORDER_EDIT_COMMIT_MUTATION,
                Map.of("id", calculatedOrderId, "notifyCustomer", false,
                        "staffNote", "Synced automatically from the BNS Warehouse System"),
                accessToken);
        JsonNode commitErrors = commitResult.path("data").path("orderEditCommit").path("userErrors");
        if (commitErrors.size() > 0) {
            return "Shopify rejected committing the order edit: " + commitErrors.get(0).path("message").asText();
        }

        return buildLineSyncMessage(pushed, notPushed, priceIncreasesSkipped, priceDecreasesSkipped);
    }

    private boolean hasEditErrors(JsonNode result, String mutationName, List<String> notPushed, String sku) {
        JsonNode errors = result.path("data").path(mutationName).path("userErrors");
        if (errors.size() > 0) {
            notPushed.add(sku + " (" + errors.get(0).path("message").asText() + ")");
            return true;
        }
        return false;
    }

    private String buildLineSyncMessage(List<String> pushed, List<String> notPushed,
                                         List<String> priceIncreasesSkipped, List<String> priceDecreasesSkipped) {
        List<String> parts = new ArrayList<>();
        if (!pushed.isEmpty()) {
            parts.add("Pushed to Shopify: " + String.join(", ", pushed) + ".");
        }
        if (!notPushed.isEmpty()) {
            parts.add("Couldn't push: " + String.join("; ", notPushed) + ".");
        }
        if (!priceIncreasesSkipped.isEmpty()) {
            parts.add("Price increase on " + String.join(", ", priceIncreasesSkipped)
                    + " not pushed - Shopify has no way to raise a line above its original price. Adjust it on Shopify directly.");
        }
        if (!priceDecreasesSkipped.isEmpty()) {
            parts.add("Price decrease on " + String.join(", ", priceDecreasesSkipped)
                    + " not auto-synced yet - adjust it on Shopify directly for now.");
        }
        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    private static final String ORDER_UPDATE_MUTATION = """
            mutation OrderUpdate($input: OrderInput!) {
              orderUpdate(input: $input) {
                order { id }
                userErrors { field message }
              }
            }
            """;

    private static final String ORDER_EDIT_BEGIN_MUTATION = """
            mutation OrderEditBegin($id: ID!) {
              orderEditBegin(id: $id) {
                calculatedOrder {
                  id
                  lineItems(first: 50) {
                    nodes { id lineItem { sku } }
                  }
                }
                userErrors { field message }
              }
            }
            """;

    private static final String ORDER_EDIT_SET_QUANTITY_MUTATION = """
            mutation OrderEditSetQuantity($id: ID!, $lineItemId: ID!, $quantity: Int!) {
              orderEditSetQuantity(id: $id, lineItemId: $lineItemId, quantity: $quantity) {
                calculatedOrder { id }
                userErrors { field message }
              }
            }
            """;

    private static final String ORDER_EDIT_ADD_VARIANT_MUTATION = """
            mutation OrderEditAddVariant($id: ID!, $variantId: ID!, $quantity: Int!) {
              orderEditAddVariant(id: $id, variantId: $variantId, quantity: $quantity) {
                calculatedOrder { id }
                userErrors { field message }
              }
            }
            """;

    private static final String ORDER_EDIT_COMMIT_MUTATION = """
            mutation OrderEditCommit($id: ID!, $notifyCustomer: Boolean!, $staffNote: String) {
              orderEditCommit(id: $id, notifyCustomer: $notifyCustomer, staffNote: $staffNote) {
                order { id }
                userErrors { field message }
              }
            }
            """;

    private JsonNode graphql(String query, Map<String, Object> variables, String accessToken) throws Exception {
        String endpoint = "https://" + shopDomain + "/admin/api/" + apiVersion + "/graphql.json";
        String body = objectMapper.writeValueAsString(Map.of("query", query, "variables", variables));
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .header("X-Shopify-Access-Token", accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(response.body());
    }
}
