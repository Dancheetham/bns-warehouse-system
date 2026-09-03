package uk.co.bns.warehouse_api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.bns.warehouse_api.dto.ShopifyStatus;
import uk.co.bns.warehouse_api.dto.ShopifySyncResult;
import uk.co.bns.warehouse_api.entity.Product;
import uk.co.bns.warehouse_api.enums.TrackingType;
import uk.co.bns.warehouse_api.repository.ProductRepository;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One-directional: Shopify's product catalogue is the source of truth, this only
 * ever reads (read_products scope), never writes back. Uses the GraphQL Admin API
 * rather than REST - Shopify's own docs for admin-created custom apps only show
 * GraphQL examples, and REST access has been progressively restricted for apps
 * created since the REST deprecation, so REST calls from a fresh app can 401/403
 * even with a perfectly valid token and the right scope.
 *
 * Matches on Shopify's own variant id (a GID string) where possible, set after
 * the first sync, falling back to SKU the first time a product is seen. Tracking
 * type, default bin, and default password are staff-owned fields Shopify has no
 * concept of - a freshly synced product gets TrackingType.NONE and
 * needsReview=true rather than a guess, and an existing product's tracking
 * type/bin/password are never touched by a sync.
 */
@Service
@RequiredArgsConstructor
public class ShopifyProductSyncService {

    private static final Logger log = LoggerFactory.getLogger(ShopifyProductSyncService.class);

    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper;
    private final SettingsService settingsService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${shopify.shop-domain:}")
    private String shopDomain;

    @Value("${shopify.api-version:2025-01}")
    private String apiVersion;

    private volatile LocalDateTime lastSyncedAt;

    private static final String PRODUCTS_QUERY = """
            query GetProducts($cursor: String) {
              products(first: 100, after: $cursor) {
                edges {
                  cursor
                  node {
                    id
                    title
                    status
                    variants(first: 100) {
                      edges {
                        node {
                          id
                          sku
                          title
                          inventoryItem {
                            id
                            measurement {
                              weight { value unit }
                            }
                          }
                        }
                      }
                    }
                  }
                }
                pageInfo { hasNextPage }
              }
            }
            """;

    public boolean isConfigured() {
        return shopDomain != null && !shopDomain.isBlank()
                && currentAccessToken() != null;
    }

    private String currentAccessToken() {
        String token = settingsService.get(ShopifyOAuthService.ACCESS_TOKEN_KEY, "");
        return token.isBlank() ? null : token;
    }

    public ShopifyStatus getStatus(LocalDateTime lastCompanySyncedAt, LocalDateTime lastOrderSyncedAt,
                                    LocalDateTime lastStockPushedAt) {
        boolean appConfigured = shopDomain != null && !shopDomain.isBlank();
        boolean connected = currentAccessToken() != null;
        return new ShopifyStatus(appConfigured, connected, appConfigured ? shopDomain : null, lastSyncedAt,
                productRepository.countByNeedsReviewTrue(), productRepository.countByShopifyProductIdIsNotNull(),
                lastCompanySyncedAt, lastOrderSyncedAt, lastStockPushedAt);
    }

    // Polls rather than using Shopify webhooks - webhooks need a public HTTPS URL
    // for Shopify to push to, which this system doesn't have while it's only
    // reachable on the LAN. Revisit once this is properly hosted somewhere.
    @Scheduled(fixedDelayString = "900000", initialDelayString = "60000")
    public void scheduledSync() {
        if (!isConfigured()) return;
        try {
            ShopifySyncResult result = sync();
            log.info("Shopify product sync: {} created, {} updated, {} skipped (no SKU), {} error(s)",
                    result.created(), result.updated(), result.skippedNoSku(), result.errors().size());
        } catch (Exception e) {
            log.warn("Scheduled Shopify sync failed: {}", e.getMessage());
        }
    }

    @Transactional
    public ShopifySyncResult sync() {
        if (!isConfigured()) {
            return new ShopifySyncResult(false, null, 0, 0, 0, List.of());
        }

        int created = 0, updated = 0, skippedNoSku = 0;
        List<String> errors = new ArrayList<>();
        String endpoint = "https://" + shopDomain + "/admin/api/" + apiVersion + "/graphql.json";
        String accessToken = currentAccessToken();

        try {
            String cursor = null;
            boolean hasNextPage = true;

            while (hasNextPage) {
                String body = objectMapper.writeValueAsString(java.util.Map.of(
                        "query", PRODUCTS_QUERY,
                        "variables", cursor != null ? java.util.Map.of("cursor", cursor) : java.util.Map.of()));

                HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                        .header("X-Shopify-Access-Token", accessToken)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    errors.add("Shopify returned HTTP " + response.statusCode()
                            + " - try disconnecting and reconnecting from the Shopify Sync page");
                    break;
                }

                JsonNode root = objectMapper.readTree(response.body());
                if (root.has("errors")) {
                    errors.add("Shopify GraphQL error: " + root.path("errors").toString());
                    break;
                }

                JsonNode productsNode = root.path("data").path("products");
                JsonNode edges = productsNode.path("edges");

                for (JsonNode productEdge : edges) {
                    JsonNode shopifyProduct = productEdge.path("node");
                    String title = shopifyProduct.path("title").asText("");
                    boolean shopifyActive = "ACTIVE".equals(shopifyProduct.path("status").asText());
                    String shopifyProductId = shopifyProduct.path("id").asText(null);
                    JsonNode variantEdges = shopifyProduct.path("variants").path("edges");
                    boolean multiVariant = variantEdges.size() > 1;

                    for (JsonNode variantEdge : variantEdges) {
                        JsonNode variant = variantEdge.path("node");
                        String sku = variant.path("sku").asText("");
                        if (sku.isBlank()) {
                            skippedNoSku++;
                            continue;
                        }
                        String variantId = variant.path("id").asText(null);
                        String inventoryItemId = variant.path("inventoryItem").path("id").asText(null);
                        String name = multiVariant ? title + " - " + variant.path("title").asText("") : title;
                        BigDecimal weightKg = extractWeightKg(variant.path("inventoryItem").path("measurement").path("weight"));

                        boolean isNew = applySync(sku, variantId, inventoryItemId, shopifyProductId, name, weightKg, shopifyActive);
                        if (isNew) created++; else updated++;
                    }

                    cursor = productEdge.path("cursor").asText(cursor);
                }

                hasNextPage = productsNode.path("pageInfo").path("hasNextPage").asBoolean(false);
                if (edges.isEmpty()) hasNextPage = false; // safety net against an infinite loop
            }
        } catch (IOException | InterruptedException e) {
            if (Thread.currentThread().isInterrupted()) Thread.currentThread().interrupt();
            errors.add("Couldn't reach Shopify: " + e.getMessage());
        }

        lastSyncedAt = LocalDateTime.now();
        return new ShopifySyncResult(true, lastSyncedAt, created, updated, skippedNoSku, errors);
    }

    private boolean applySync(String sku, String variantId, String inventoryItemId, String shopifyProductId,
                               String name, BigDecimal weightKg, boolean shopifyActive) {
        Optional<Product> existing = variantId != null
                ? productRepository.findByShopifyVariantId(variantId)
                : Optional.empty();
        if (existing.isEmpty()) {
            existing = productRepository.findBySkuIgnoreCase(sku);
        }

        Product product = existing.orElseGet(Product::new);
        boolean isNew = product.getId() == null;

        if (isNew) {
            product.setSku(sku.trim().toUpperCase());
            product.setTrackingType(TrackingType.NONE);
            product.setNeedsReview(true);
            product.setActive(shopifyActive);
        }
        product.setName(name);
        // Weight only ever flows warehouse -> Shopify from here on (see
        // ShopifyInventoryPushService) - the warehouse system is authoritative,
        // so a pulled Shopify weight is only used as a starting value for a
        // product we've genuinely never seen before, never to overwrite a
        // value we already have. Otherwise a Shopify-side edit would silently
        // win on the next sync, exactly the inconsistent behaviour this was
        // built to avoid.
        if (isNew || product.getWeightKg() == null) {
            product.setWeightKg(weightKg);
        }
        product.setShopifyProductId(shopifyProductId);
        product.setShopifyVariantId(variantId);
        product.setShopifyInventoryItemId(inventoryItemId);
        product.setLastSyncedAt(LocalDateTime.now());
        productRepository.save(product);
        return isNew;
    }

    private BigDecimal extractWeightKg(JsonNode weightNode) {
        if (weightNode.isMissingNode() || weightNode.isNull()) return null;
        double value = weightNode.path("value").asDouble(0);
        String unit = weightNode.path("unit").asText("");
        if (value <= 0) return null;

        double kg = switch (unit) {
            case "GRAMS" -> value / 1000.0;
            case "KILOGRAMS" -> value;
            case "POUNDS" -> value * 0.45359237;
            case "OUNCES" -> value * 0.028349523;
            default -> value;
        };
        return BigDecimal.valueOf(kg).setScale(3, RoundingMode.HALF_UP);
    }
}
