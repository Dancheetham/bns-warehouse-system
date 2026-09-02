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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pushes despatch confirmation back to Shopify as a real Fulfillment record -
 * this is what makes the order show as shipped on the customer's Shopify
 * account/order status page. Deliberately notifyCustomer: false always -
 * Shopify's own "your order has shipped" email can't include MAC/serial/
 * password, so BNS's own despatch confirmation email (DespatchConfirmationService)
 * is the one that actually goes to the customer; this call only updates status.
 *
 * Uses fulfillmentCreate (not the deprecated fulfillmentCreateV2). Best-effort:
 * any failure here is logged and swallowed, never allowed to block the actual
 * despatch confirmation in the warehouse - Shopify being briefly unreachable
 * shouldn't stop stock going out the door.
 */
@Service
@RequiredArgsConstructor
public class ShopifyFulfillmentService {

    private static final Logger log = LoggerFactory.getLogger(ShopifyFulfillmentService.class);

    private final ObjectMapper objectMapper;
    private final SettingsService settingsService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${shopify.shop-domain:}")
    private String shopDomain;

    @Value("${shopify.api-version:2025-01}")
    private String apiVersion;

    private static final String FULFILLMENT_ORDERS_QUERY = """
            query GetFulfillmentOrders($orderId: ID!) {
              order(id: $orderId) {
                fulfillmentOrders(first: 10) {
                  nodes {
                    id
                    lineItems(first: 50) {
                      nodes {
                        id
                        remainingQuantity
                        lineItem { sku }
                      }
                    }
                  }
                }
              }
            }
            """;

    private static final String FULFILLMENT_CREATE_MUTATION = """
            mutation FulfillmentCreate($fulfillment: FulfillmentInput!) {
              fulfillmentCreate(fulfillment: $fulfillment) {
                fulfillment { id status }
                userErrors { field message }
              }
            }
            """;

    /** Best-effort, never throws - returns a short human-readable outcome for display. */
    public String pushFulfillment(Order order, String dummyTrackingNumber) {
        if (order.getShopifyOrderId() == null) {
            return "Not a Shopify order - nothing to push";
        }
        String accessToken = settingsService.get(ShopifyOAuthService.ACCESS_TOKEN_KEY, "");
        if (shopDomain.isBlank() || accessToken.isBlank()) {
            return "Shopify isn't connected - couldn't push fulfillment";
        }

        Map<String, Integer> despatchedQtyBySku = new LinkedHashMap<>();
        for (OrderLine line : order.getLines()) {
            Integer qty = line.getQuantityDespatched();
            if (qty == null || qty <= 0) continue;
            despatchedQtyBySku.merge(line.getProduct().getSku(), qty, Integer::sum);
        }
        if (despatchedQtyBySku.isEmpty()) {
            return "Nothing despatched to push";
        }

        String endpoint = "https://" + shopDomain + "/admin/api/" + apiVersion + "/graphql.json";

        try {
            JsonNode foResult = graphql(endpoint, accessToken, FULFILLMENT_ORDERS_QUERY,
                    Map.of("orderId", order.getShopifyOrderId()));
            if (foResult.has("errors")) {
                log.warn("Shopify fulfillment order lookup failed for {}: {}", order.getOrderNumber(), foResult.path("errors"));
                return "Couldn't look up Shopify's fulfillment orders";
            }

            JsonNode fulfillmentOrders = foResult.path("data").path("order").path("fulfillmentOrders").path("nodes");
            List<Map<String, Object>> lineItemsByFO = new ArrayList<>();

            for (JsonNode fo : fulfillmentOrders) {
                String fulfillmentOrderId = fo.path("id").asText();
                List<Map<String, Object>> matched = new ArrayList<>();

                for (JsonNode li : fo.path("lineItems").path("nodes")) {
                    String sku = li.path("lineItem").path("sku").asText(null);
                    if (sku == null) continue;
                    Integer remainingToDespatch = despatchedQtyBySku.get(sku);
                    if (remainingToDespatch == null || remainingToDespatch <= 0) continue;

                    int remainingOnShopify = li.path("remainingQuantity").asInt(0);
                    int quantity = Math.min(remainingToDespatch, remainingOnShopify);
                    if (quantity <= 0) continue;

                    matched.add(Map.of("id", li.path("id").asText(), "quantity", quantity));
                    despatchedQtyBySku.put(sku, remainingToDespatch - quantity);
                }

                if (!matched.isEmpty()) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("fulfillmentOrderId", fulfillmentOrderId);
                    entry.put("fulfillmentOrderLineItems", matched);
                    lineItemsByFO.add(entry);
                }
            }

            if (lineItemsByFO.isEmpty()) {
                return "No matching Shopify fulfillment line items found (already fulfilled there, or SKUs don't match)";
            }

            Map<String, Object> fulfillmentInput = new LinkedHashMap<>();
            fulfillmentInput.put("lineItemsByFulfillmentOrder", lineItemsByFO);
            fulfillmentInput.put("notifyCustomer", false);
            if (dummyTrackingNumber != null && !dummyTrackingNumber.isBlank()) {
                fulfillmentInput.put("trackingInfo", Map.of("company", "Sample Courier (placeholder)", "number", dummyTrackingNumber));
            }

            JsonNode createResult = graphql(endpoint, accessToken, FULFILLMENT_CREATE_MUTATION, Map.of("fulfillment", fulfillmentInput));
            JsonNode userErrors = createResult.path("data").path("fulfillmentCreate").path("userErrors");
            if (userErrors.size() > 0) {
                log.warn("Shopify fulfillmentCreate userErrors for {}: {}", order.getOrderNumber(), userErrors);
                return "Shopify rejected the fulfillment: " + userErrors.get(0).path("message").asText();
            }
            if (createResult.has("errors")) {
                log.warn("Shopify fulfillmentCreate GraphQL error for {}: {}", order.getOrderNumber(), createResult.path("errors"));
                return "Shopify GraphQL error creating the fulfillment";
            }

            return "Pushed to Shopify - order now shows as shipped there too";
        } catch (Exception e) {
            log.warn("Failed to push fulfillment to Shopify for order {}: {}", order.getOrderNumber(), e.getMessage());
            return "Couldn't reach Shopify: " + e.getMessage();
        }
    }

    private JsonNode graphql(String endpoint, String accessToken, String query, Map<String, Object> variables) throws Exception {
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
