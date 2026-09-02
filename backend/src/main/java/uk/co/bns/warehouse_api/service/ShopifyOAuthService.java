package uk.co.bns.warehouse_api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.co.bns.warehouse_api.exception.ValidationException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * A previous attempt at this integration used the app's OAuth Client Secret
 * (visible in Dev Dashboard under a plain "Secret" label, easily mistaken for an
 * access token) directly as the Admin API access token - which always 401s,
 * since a client secret is only ever meant to be used server-side, in this exact
 * exchange, never sent to the API as a bearer credential itself.
 *
 * This does the handshake properly: redirect the merchant to Shopify's consent
 * screen, catch the authorization code on the way back, and exchange it
 * server-to-server (with the secret, which never leaves the backend) for a real,
 * long-lived offline access token. That token is then stored via SettingsService
 * - dynamically, not as an env var - so reconnecting doesn't require editing
 * docker-compose.yml or restarting the stack.
 */
@Service
@RequiredArgsConstructor
public class ShopifyOAuthService {

    private final SettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public static final String ACCESS_TOKEN_KEY = "shopify_access_token";
    private static final String SCOPES = "read_products";

    @Value("${shopify.shop-domain:}")
    private String shopDomain;

    @Value("${shopify.client-id:}")
    private String clientId;

    @Value("${shopify.client-secret:}")
    private String clientSecret;

    // Single-tenant internal tool, one connect flow at a time - a simple
    // in-memory CSRF check is enough here, no need for persistent storage.
    private volatile String pendingState;

    public boolean isAppConfigured() {
        return notBlank(shopDomain) && notBlank(clientId) && notBlank(clientSecret);
    }

    public boolean isConnected() {
        return notBlank(settingsService.get(ACCESS_TOKEN_KEY, ""));
    }

    public String getShopDomain() {
        return shopDomain;
    }

    public String buildAuthorizeUrl(String redirectUri) {
        if (!isAppConfigured()) {
            throw new ValidationException(
                    "Shopify isn't configured - set SHOPIFY_SHOP_DOMAIN, SHOPIFY_CLIENT_ID and SHOPIFY_CLIENT_SECRET");
        }
        String state = UUID.randomUUID().toString();
        this.pendingState = state;

        return "https://" + shopDomain + "/admin/oauth/authorize"
                + "?client_id=" + encode(clientId)
                + "&scope=" + encode(SCOPES)
                + "&redirect_uri=" + encode(redirectUri)
                + "&state=" + encode(state);
    }

    public void handleCallback(String shop, String code, String state) {
        if (shop == null || !shop.equalsIgnoreCase(shopDomain)) {
            throw new ValidationException("Unexpected shop in callback: " + shop);
        }
        if (pendingState == null || !pendingState.equals(state)) {
            throw new ValidationException("OAuth state didn't match - please try connecting again");
        }
        pendingState = null;

        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "client_id", clientId,
                    "client_secret", clientSecret,
                    "code", code));

            HttpRequest request = HttpRequest.newBuilder(URI.create("https://" + shopDomain + "/admin/oauth/access_token"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ValidationException("Shopify rejected the token exchange (HTTP " + response.statusCode() + ")");
            }

            JsonNode json = objectMapper.readTree(response.body());
            String accessToken = json.path("access_token").asText(null);
            if (accessToken == null || accessToken.isBlank()) {
                throw new ValidationException("Shopify's response didn't include an access token");
            }

            settingsService.set(ACCESS_TOKEN_KEY, accessToken);
        } catch (IOException | InterruptedException e) {
            if (Thread.currentThread().isInterrupted()) Thread.currentThread().interrupt();
            throw new ValidationException("Couldn't reach Shopify to complete the connection: " + e.getMessage());
        }
    }

    public void disconnect() {
        settingsService.set(ACCESS_TOKEN_KEY, "");
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
