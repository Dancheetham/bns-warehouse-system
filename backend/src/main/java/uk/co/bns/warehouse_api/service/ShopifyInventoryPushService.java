package uk.co.bns.warehouse_api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uk.co.bns.warehouse_api.dto.ShopifyStockPushResult;
import uk.co.bns.warehouse_api.entity.Product;
import uk.co.bns.warehouse_api.enums.StockItemStatus;
import uk.co.bns.warehouse_api.repository.ProductRepository;
import uk.co.bns.warehouse_api.repository.StockItemRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pushes weight and available-stock quantity FROM the warehouse system TO
 * Shopify - the reverse direction of the product sync, and deliberately
 * one-way: the warehouse system is authoritative for both. Weight pushes
 * immediately whenever a product is saved (rare, deliberate edits, so no
 * need for a periodic batch); stock quantity pushes on a timer, since dozens
 * of different actions touch stock and hooking each one individually would
 * be fragile.
 *
 * Both target the InventoryItem object family (not Product/ProductVariant),
 * which is why both only need the write_inventory scope - no write_products
 * needed, despite weight conceptually feeling like "a product field".
 */
@Service
@RequiredArgsConstructor
public class ShopifyInventoryPushService {

    private static final Logger log = LoggerFactory.getLogger(ShopifyInventoryPushService.class);

    private final ProductRepository productRepository;
    private final StockItemRepository stockItemRepository;
    private final SettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${shopify.shop-domain:}")
    private String shopDomain;

    @Value("${shopify.api-version:2026-04}")
    private String apiVersion;

    private volatile LocalDateTime lastStockPushedAt;

    public LocalDateTime getLastStockPushedAt() {
        return lastStockPushedAt;
    }

    private static final String LOCATIONS_QUERY = """
            query GetLocations {
              locations(first: 10) {
                nodes { id name }
              }
            }
            """;

    private static final String INVENTORY_ITEM_UPDATE_MUTATION = """
            mutation InventoryItemUpdate($id: ID!, $input: InventoryItemInput!) {
              inventoryItemUpdate(id: $id, input: $input) {
                inventoryItem { id }
                userErrors { field message }
              }
            }
            """;

    // idempotencyKey via the @idempotent directive is required as of API
    // version 2026-04 - a UUID generated fresh per call, see pushStockLevels.
    private static final String INVENTORY_SET_QUANTITIES_MUTATION = """
            mutation InventorySetQuantities($input: InventorySetQuantitiesInput!, $idempotencyKey: String!) {
              inventorySetQuantities(input: $input) @idempotent(key: $idempotencyKey) {
                userErrors { code field message }
              }
            }
            """;

    private static final String LOCATION_SETTING_KEY = "shopify_location_id";

    public void pushWeight(Product product) {
        if (product.getShopifyInventoryItemId() == null || product.getWeightKg() == null) return;
        String accessToken = accessToken();
        if (shopDomain.isBlank() || accessToken == null) return;

        try {
            Map<String, Object> input = Map.of(
                    "measurement", Map.of(
                            "weight", Map.of("value", product.getWeightKg().doubleValue(), "unit", "KILOGRAMS")
                    )
            );
            JsonNode result = graphql(accessToken, INVENTORY_ITEM_UPDATE_MUTATION,
                    Map.of("id", product.getShopifyInventoryItemId(), "input", input));

            JsonNode userErrors = result.path("data").path("inventoryItemUpdate").path("userErrors");
            if (userErrors.size() > 0) {
                log.warn("Shopify rejected weight push for {}: {}", product.getSku(), userErrors);
            } else if (result.has("errors")) {
                log.warn("Shopify GraphQL error pushing weight for {}: {}", product.getSku(), result.path("errors"));
            }
        } catch (Exception e) {
            log.warn("Couldn't push weight to Shopify for {}: {}", product.getSku(), e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "600000", initialDelayString = "75000")
    public void scheduledStockPush() {
        try {
            ShopifyStockPushResult result = pushStockLevels();
            if (result.configured()) {
                log.info("Shopify stock push: {} pushed, {} skipped, {} error(s)",
                        result.pushed(), result.skipped().size(), result.errors().size());
            }
        } catch (Exception e) {
            log.warn("Scheduled Shopify stock push failed: {}", e.getMessage());
        }
    }

    public ShopifyStockPushResult pushStockLevels() {
        String accessToken = accessToken();
        if (shopDomain.isBlank() || accessToken == null) {
            return new ShopifyStockPushResult(false, null, 0, List.of(), List.of());
        }

        List<String> errors = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        String locationId = resolveLocationId(accessToken, errors);
        if (locationId == null) {
            lastStockPushedAt = LocalDateTime.now();
            return new ShopifyStockPushResult(true, lastStockPushedAt, 0, skipped, errors);
        }

        List<Product> linkedProducts = productRepository.findByShopifyInventoryItemIdIsNotNull();
        int pushed = 0;
        int chunkSize = 100;

        for (int i = 0; i < linkedProducts.size(); i += chunkSize) {
            List<Product> chunk = linkedProducts.subList(i, Math.min(i + chunkSize, linkedProducts.size()));
            List<Map<String, Object>> quantities = new ArrayList<>();
            for (Product product : chunk) {
                long available = stockItemRepository.countByProduct_IdAndStatus(product.getId(), StockItemStatus.AVAILABLE);
                // changeFromQuantity has to be present as a key on every item
                // (confirmed by a live schema error, twice now, not a guess) -
                // but explicitly passing null for it opts out of Shopify's
                // compare-and-swap check entirely, which is exactly "just set
                // the absolute value" - the behaviour actually wanted. Map.of()
                // can't hold a null value at all (throws), hence the mutable
                // map here instead of the Map.of() used everywhere else in
                // this file.
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("inventoryItemId", product.getShopifyInventoryItemId());
                item.put("locationId", locationId);
                item.put("quantity", (int) available);
                item.put("changeFromQuantity", null);
                quantities.add(item);
            }
            if (quantities.isEmpty()) continue;

            try {
                Map<String, Object> input = new LinkedHashMap<>();
                input.put("name", "available");
                input.put("reason", "correction");
                // ignoreCompareQuantity isn't a real field on this input type -
                // confirmed by Shopify's own schema validation error, not just
                // a search result this time. Leaving compareQuantity out
                // entirely (never set below) means there's nothing to compare
                // against, so this just sets the value directly - exactly the
                // "warehouse is authoritative" behaviour wanted, no flag needed.
                input.put("referenceDocumentUri", "warehouse://bns-warehouse-system/stock-sync");
                input.put("quantities", quantities);

                JsonNode result = graphql(accessToken, INVENTORY_SET_QUANTITIES_MUTATION,
                        Map.of("input", input, "idempotencyKey", UUID.randomUUID().toString()));

                JsonNode userErrors = result.path("data").path("inventorySetQuantities").path("userErrors");
                if (userErrors.size() > 0) {
                    errors.add("Shopify rejected a stock batch: " + userErrors);
                } else if (result.has("errors")) {
                    errors.add("Shopify GraphQL error: " + result.path("errors"));
                } else {
                    pushed += quantities.size();
                }
            } catch (Exception e) {
                errors.add("Couldn't reach Shopify: " + e.getMessage());
            }
        }

        lastStockPushedAt = LocalDateTime.now();
        return new ShopifyStockPushResult(true, lastStockPushedAt, pushed, skipped, errors);
    }

    private String resolveLocationId(String accessToken, List<String> errors) {
        String cached = settingsService.get(LOCATION_SETTING_KEY, "");
        if (!cached.isBlank()) return cached;

        try {
            JsonNode result = graphql(accessToken, LOCATIONS_QUERY, Map.of());
            if (result.has("errors")) {
                // Was previously silently treated the same as "no locations
                // found", which is exactly wrong - a permission error and an
                // empty result are very different problems with very
                // different fixes, and burying this made a real scope gap
                // (read_locations, separate from read_inventory - Shopify's
                // Location object has needed its own scope since API
                // version 2024-07) look like empty data instead.
                errors.add("Shopify GraphQL error looking up locations: " + result.path("errors"));
                return null;
            }
            JsonNode locations = result.path("data").path("locations").path("nodes");
            if (locations.size() == 1) {
                String id = locations.get(0).path("id").asText();
                settingsService.set(LOCATION_SETTING_KEY, id);
                return id;
            } else if (locations.size() == 0) {
                errors.add("No Shopify locations found - stock can't be pushed anywhere");
            } else {
                errors.add("Shopify has " + locations.size()
                        + " locations - can't auto-pick which one to push stock to. Manually configuring which location isn't built yet.");
            }
        } catch (Exception e) {
            errors.add("Couldn't look up Shopify locations: " + e.getMessage());
        }
        return null;
    }

    private String accessToken() {
        String token = settingsService.get(ShopifyOAuthService.ACCESS_TOKEN_KEY, "");
        return token.isBlank() ? null : token;
    }

    private JsonNode graphql(String accessToken, String query, Map<String, Object> variables) throws Exception {
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
