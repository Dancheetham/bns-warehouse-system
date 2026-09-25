package uk.co.bns.warehouse_api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.co.bns.warehouse_api.entity.Invoice;
import uk.co.bns.warehouse_api.entity.Order;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/**
 * Reflects a Payment Tracking payment back onto the originating Shopify
 * order, so its financial status isn't stuck showing "pending" forever once
 * BNS has actually collected the money offline (these are net-terms B2B
 * orders, not paid at Shopify checkout).
 *
 * Deliberately limited to the case this integration can actually do
 * correctly today: this app's whole Shopify integration is GraphQL-only (see
 * ShopifyProductSyncService's own comment on why - REST access is
 * restricted for apps created since its deprecation), and the GraphQL Admin
 * API's orderMarkAsPaid mutation only supports marking an order as FULLY
 * paid - there's no GraphQL equivalent (as of the API version this app
 * targets) for recording an arbitrary partial manual payment the way the
 * legacy REST Transactions endpoint could. So: a payment that brings a
 * single-order invoice to fully paid gets pushed; a partial payment, or an
 * invoice covering more than one order (Company.invoiceGrouping =
 * CONSOLIDATED), is reported back as not pushed rather than guessed at.
 * If partial sync turns out to matter enough to chase down properly, it
 * needs verifying against BNS's actual store/API access first - flagging
 * that rather than shipping an unverified call.
 */
@Service
@RequiredArgsConstructor
public class ShopifyPaymentPushService {

    private static final Logger log = LoggerFactory.getLogger(ShopifyPaymentPushService.class);

    private final ObjectMapper objectMapper;
    private final SettingsService settingsService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${shopify.shop-domain:}")
    private String shopDomain;

    @Value("${shopify.api-version:2025-01}")
    private String apiVersion;

    private static final String ORDER_MARK_AS_PAID_MUTATION = """
            mutation OrderMarkAsPaid($input: OrderMarkAsPaidInput!) {
              orderMarkAsPaid(input: $input) {
                order { id displayFinancialStatus }
                userErrors { field message }
              }
            }
            """;

    /** Best-effort, never throws - returns a short human-readable outcome, or null if there was nothing to push at all. */
    public String pushIfFullyPaid(Invoice invoice) {
        List<Order> orders = invoice.getLines().stream().map(l -> l.getOrder()).distinct().toList();
        if (orders.size() != 1) {
            return orders.size() > 1
                    ? "Not pushed to Shopify - this invoice covers more than one order (consolidated invoicing), which this integration doesn't sync payment status for"
                    : null;
        }
        Order order = orders.get(0);
        if (order.getShopifyOrderId() == null) {
            return null; // not a Shopify order - nothing to push, nothing worth telling anyone about
        }
        if (invoice.getPaidAmount().compareTo(invoice.getGrandTotal()) < 0) {
            return "Not pushed to Shopify yet - this invoice is only partially paid, and this integration can only "
                    + "sync a Shopify order as fully paid (see ShopifyPaymentPushService for why)";
        }

        String accessToken = settingsService.get(ShopifyOAuthService.ACCESS_TOKEN_KEY, "");
        if (shopDomain.isBlank() || accessToken.isBlank()) {
            return "Shopify isn't connected - couldn't push payment status";
        }

        String endpoint = "https://" + shopDomain + "/admin/api/" + apiVersion + "/graphql.json";
        try {
            JsonNode result = graphql(endpoint, accessToken, ORDER_MARK_AS_PAID_MUTATION,
                    Map.of("input", Map.of("id", order.getShopifyOrderId())));
            JsonNode userErrors = result.path("data").path("orderMarkAsPaid").path("userErrors");
            if (userErrors.size() > 0) {
                log.warn("Shopify orderMarkAsPaid userErrors for {}: {}", order.getOrderNumber(), userErrors);
                return "Shopify rejected marking the order as paid: " + userErrors.get(0).path("message").asText();
            }
            if (result.has("errors")) {
                log.warn("Shopify orderMarkAsPaid GraphQL error for {}: {}", order.getOrderNumber(), result.path("errors"));
                return "Shopify GraphQL error marking the order as paid";
            }
            return "Pushed to Shopify - order now shows as paid there too";
        } catch (Exception e) {
            log.warn("Failed to push payment status to Shopify for order {}: {}", order.getOrderNumber(), e.getMessage());
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
