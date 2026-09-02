package uk.co.bns.warehouse_api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.co.bns.warehouse_api.dto.ShopifyOrderSyncResult;
import uk.co.bns.warehouse_api.entity.Order;
import uk.co.bns.warehouse_api.repository.OrderRepository;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pulls orders in from Shopify - the main sales channel going forward. Every
 * order lands as ON_HOLD regardless of company/credit status, so it always gets
 * a human look and (if linked to a company) a credit check before release -
 * matching the "block at release, not at Shopify checkout" decision.
 *
 * An order is matched to a company via Order.purchasingEntity, which Shopify
 * itself resolves for B2B checkouts - not by guessing from customer email. A
 * D2C order (no company) imports the same way, just without a linked company,
 * so it never gets credit-checked.
 *
 * If ANY line can't be matched to a product by SKU, the whole order is held
 * back rather than imported with a line missing. It's retried automatically on
 * the next sync once the product catalogue is fixed.
 *
 * Deliberately NOT @Transactional here - this method does network I/O and
 * orchestrates many orders; the actual per-order write happens in
 * ShopifyOrderImportService, each in its own transaction, so one bad order
 * can't roll back everything else in the batch.
 */
@Service
@RequiredArgsConstructor
public class ShopifyOrderSyncService {

    private final OrderRepository orderRepository;
    private final ShopifyOrderImportService shopifyOrderImportService;
    private final ObjectMapper objectMapper;
    private final SettingsService settingsService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${shopify.shop-domain:}")
    private String shopDomain;

    @Value("${shopify.api-version:2025-01}")
    private String apiVersion;

    private volatile LocalDateTime lastSyncedAt;

    public LocalDateTime getLastSyncedAt() {
        return lastSyncedAt;
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ShopifyOrderSyncService.class);

    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "120000", initialDelayString = "45000")
    public void scheduledSync() {
        try {
            ShopifyOrderSyncResult result = sync();
            if (result.configured()) {
                log.info("Shopify order sync: {} imported, {} already imported, {} skipped, {} error(s)",
                        result.imported(), result.alreadyImported(), result.skipped().size(), result.errors().size());
            }
        } catch (Exception e) {
            log.warn("Scheduled Shopify order sync failed: {}", e.getMessage());
        }
    }

    private static final String ORDERS_QUERY = """
            query GetOrders($cursor: String) {
              orders(first: 25, after: $cursor, sortKey: CREATED_AT, reverse: true) {
                edges {
                  cursor
                  node {
                    id
                    name
                    createdAt
                    note
                    poNumber
                    customer { firstName lastName email }
                    shippingAddress { name city province provinceCode country countryCodeV2 zip }
                    totalShippingPriceSet { shopMoney { amount } }
                    purchasingEntity {
                      __typename
                      ... on Customer {
                        displayName
                        email
                      }
                      ... on PurchasingCompany {
                        company { id name }
                        contact {
                          customer { displayName email }
                        }
                      }
                    }
                    lineItems(first: 100) {
                      edges {
                        node {
                          sku
                          name
                          quantity
                          originalUnitPriceSet { shopMoney { amount } }
                        }
                      }
                    }
                  }
                }
                pageInfo { hasNextPage }
              }
            }
            """;

    private static final String ORDER_ID_BY_NAME_QUERY = """
            query GetOrderIdByName($query: String!) {
              orders(first: 1, query: $query) {
                nodes { id }
              }
            }
            """;

    /**
     * Orders synced before shopifyOrderId started being captured have
     * ecommerceOrderNumber set but no GID, silently breaking the fulfillment
     * push (it looks like "not a Shopify order" even though it is one). Backfills
     * them by looking each up on Shopify by its display name. Best-effort, runs
     * automatically at the start of every sync rather than needing a manual step.
     */
    private void backfillMissingShopifyOrderIds(String endpoint, String accessToken) {
        List<Order> toBackfill = orderRepository.findByEcommerceOrderNumberIsNotNullAndShopifyOrderIdIsNull();
        for (Order order : toBackfill) {
            try {
                // Shopify's name field displays with a leading "#" (e.g. "#1010")
                // but its own search syntax filters on the number without it.
                String nameQuery = "name:" + order.getEcommerceOrderNumber().replaceFirst("^#", "");
                JsonNode result = objectMapper.readTree(sendGraphql(endpoint, accessToken, ORDER_ID_BY_NAME_QUERY,
                        Map.of("query", nameQuery)));
                JsonNode nodes = result.path("data").path("orders").path("nodes");
                if (nodes.size() > 0) {
                    order.setShopifyOrderId(nodes.get(0).path("id").asText());
                    orderRepository.save(order);
                    log.info("Backfilled Shopify order id for {}", order.getOrderNumber());
                }
            } catch (Exception e) {
                log.warn("Couldn't backfill Shopify order id for {}: {}", order.getOrderNumber(), e.getMessage());
            }
        }
    }

    private String sendGraphql(String endpoint, String accessToken, String query, Map<String, Object> variables)
            throws IOException, InterruptedException {
        String body = objectMapper.writeValueAsString(Map.of("query", query, "variables", variables));
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .header("X-Shopify-Access-Token", accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    public ShopifyOrderSyncResult sync() {
        String accessToken = settingsService.get(ShopifyOAuthService.ACCESS_TOKEN_KEY, "");
        if (shopDomain.isBlank() || accessToken.isBlank()) {
            return new ShopifyOrderSyncResult(false, null, 0, 0, List.of(), List.of());
        }

        String endpoint = "https://" + shopDomain + "/admin/api/" + apiVersion + "/graphql.json";
        backfillMissingShopifyOrderIds(endpoint, accessToken);

        int imported = 0, alreadyImported = 0;
        List<String> skipped = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try {
            String cursor = null;
            boolean hasNextPage = true;
            int consecutiveAlreadyImported = 0;

            while (hasNextPage && consecutiveAlreadyImported < 10) {
                String body = objectMapper.writeValueAsString(Map.of(
                        "query", ORDERS_QUERY,
                        "variables", cursor != null ? Map.of("cursor", cursor) : Map.of()));

                HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                        .header("X-Shopify-Access-Token", accessToken)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    errors.add("Shopify returned HTTP " + response.statusCode()
                            + " - check the connection has the read_orders scope");
                    break;
                }

                JsonNode root = objectMapper.readTree(response.body());
                if (root.has("errors")) {
                    errors.add("Shopify GraphQL error: " + root.path("errors").toString());
                    break;
                }

                JsonNode ordersNode = root.path("data").path("orders");
                JsonNode edges = ordersNode.path("edges");

                for (JsonNode edge : edges) {
                    JsonNode node = edge.path("node");
                    String shopifyOrderNumber = node.path("name").asText(null);
                    cursor = edge.path("cursor").asText(cursor);

                    if (shopifyOrderNumber != null && orderRepository.existsByEcommerceOrderNumber(shopifyOrderNumber)) {
                        alreadyImported++;
                        consecutiveAlreadyImported++;
                        continue;
                    }
                    consecutiveAlreadyImported = 0;

                    try {
                        String outcome = shopifyOrderImportService.importOrder(node, shopifyOrderNumber);
                        if (outcome == null) {
                            imported++;
                        } else {
                            skipped.add(outcome);
                        }
                    } catch (Exception e) {
                        skipped.add(shopifyOrderNumber + ": failed - " + e.getMessage());
                    }
                }

                hasNextPage = ordersNode.path("pageInfo").path("hasNextPage").asBoolean(false);
                if (edges.isEmpty()) hasNextPage = false;
            }
        } catch (IOException | InterruptedException e) {
            if (Thread.currentThread().isInterrupted()) Thread.currentThread().interrupt();
            errors.add("Couldn't reach Shopify: " + e.getMessage());
        }

        lastSyncedAt = LocalDateTime.now();
        return new ShopifyOrderSyncResult(true, lastSyncedAt, imported, alreadyImported, skipped, errors);
    }
}
