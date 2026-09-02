package uk.co.bns.warehouse_api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.bns.warehouse_api.dto.ShopifyCompanySyncResult;
import uk.co.bns.warehouse_api.entity.Company;
import uk.co.bns.warehouse_api.repository.CompanyRepository;

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
 * Pulls Shopify's native B2B Company objects in - not BSS's, Shopify's own,
 * since that's what genuinely determines which company an order was placed
 * under (see ShopifyOrderSyncService). Credit limit is never pulled from here -
 * it's warehouse-owned and set by staff on the Companies page. Only the
 * name/existence syncs; matching is done by Shopify's own company id
 * (Company.shopifyCompanyId), never by name (names can collide or get renamed).
 */
@Service
@RequiredArgsConstructor
public class ShopifyCompanySyncService {

    private final CompanyRepository companyRepository;
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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ShopifyCompanySyncService.class);

    // Companies change rarely (new B2B account signed up) - a slower poll than
    // orders is plenty, and it must run before order sync ever gets a chance to
    // link a brand-new company's first order, though a late link there just
    // means that one order goes uncredit-checked until fixed by hand - not a
    // silent failure.
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "900000", initialDelayString = "30000")
    public void scheduledSync() {
        try {
            uk.co.bns.warehouse_api.dto.ShopifyCompanySyncResult result = sync();
            if (result.configured()) {
                log.info("Shopify company sync: {} created, {} updated, {} error(s)",
                        result.created(), result.updated(), result.errors().size());
            }
        } catch (Exception e) {
            log.warn("Scheduled Shopify company sync failed: {}", e.getMessage());
        }
    }

    private static final String COMPANIES_QUERY = """
            query GetCompanies($cursor: String) {
              companies(first: 50, after: $cursor) {
                edges {
                  cursor
                  node { id name }
                }
                pageInfo { hasNextPage }
              }
            }
            """;

    @Transactional
    public ShopifyCompanySyncResult sync() {
        String accessToken = settingsService.get(ShopifyOAuthService.ACCESS_TOKEN_KEY, "");
        if (shopDomain.isBlank() || accessToken.isBlank()) {
            return new ShopifyCompanySyncResult(false, null, 0, 0, List.of());
        }

        int created = 0, updated = 0;
        List<String> errors = new ArrayList<>();
        String endpoint = "https://" + shopDomain + "/admin/api/" + apiVersion + "/graphql.json";

        try {
            String cursor = null;
            boolean hasNextPage = true;

            while (hasNextPage) {
                String body = objectMapper.writeValueAsString(Map.of(
                        "query", COMPANIES_QUERY,
                        "variables", cursor != null ? Map.of("cursor", cursor) : Map.of()));

                HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                        .header("X-Shopify-Access-Token", accessToken)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    errors.add("Shopify returned HTTP " + response.statusCode()
                            + " - check the connection has the read_companies scope");
                    break;
                }

                JsonNode root = objectMapper.readTree(response.body());
                if (root.has("errors")) {
                    errors.add("Shopify GraphQL error: " + root.path("errors").toString());
                    break;
                }

                JsonNode companiesNode = root.path("data").path("companies");
                JsonNode edges = companiesNode.path("edges");

                for (JsonNode edge : edges) {
                    JsonNode node = edge.path("node");
                    String shopifyId = node.path("id").asText(null);
                    String name = node.path("name").asText("");
                    if (shopifyId == null) continue;

                    Company company = companyRepository.findByShopifyCompanyId(shopifyId).orElseGet(Company::new);
                    boolean isNew = company.getId() == null;
                    company.setShopifyCompanyId(shopifyId);
                    company.setName(name);
                    companyRepository.save(company);
                    if (isNew) created++; else updated++;

                    cursor = edge.path("cursor").asText(cursor);
                }

                hasNextPage = companiesNode.path("pageInfo").path("hasNextPage").asBoolean(false);
                if (edges.isEmpty()) hasNextPage = false;
            }
        } catch (IOException | InterruptedException e) {
            if (Thread.currentThread().isInterrupted()) Thread.currentThread().interrupt();
            errors.add("Couldn't reach Shopify: " + e.getMessage());
        }

        lastSyncedAt = LocalDateTime.now();
        return new ShopifyCompanySyncResult(true, lastSyncedAt, created, updated, errors);
    }
}
