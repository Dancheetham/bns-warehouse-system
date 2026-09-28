package uk.co.bns.warehouse_api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.co.bns.warehouse_api.exception.ValidationException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Handles DPD's own auth flow (separate from and unrelated to BNS's own user
 * login): a Basic-auth exchange of the account's API key/secret for a JWT
 * access token (valid 24h) plus a refresh token (valid 7 days). Cached
 * in-memory here so every DPD call doesn't re-authenticate - refreshed a
 * little before actual expiry rather than right up against it, and if the
 * refresh token itself has gone stale (nothing has shipped in >7 days), we
 * just fall back to a fresh Basic-auth login rather than failing.
 *
 * Sandbox vs live is a per-BNS-account setting (dpd_environment), not a
 * per-request choice - BNS only ever operates against one DPD account at a
 * time.
 */
@Service
@RequiredArgsConstructor
public class DpdAuthService {

    private static final Logger log = LoggerFactory.getLogger(DpdAuthService.class);

    private static final String SANDBOX_BASE_URL = "https://developers.api.customers.dpd.co.uk";
    private static final String LIVE_BASE_URL = "https://api.customers.dpd.co.uk";

    // Refresh a couple of minutes early rather than racing actual expiry.
    private static final long EXPIRY_SAFETY_MARGIN_SECONDS = 120;

    private final SettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private volatile String cachedAccessToken;
    private volatile String cachedRefreshToken;
    private volatile Instant accessTokenExpiresAt = Instant.EPOCH;
    // What dpd_environment/dpd_api_key/dpd_api_secret were set to at the
    // moment the cached token above was actually issued - see the
    // credentialsChanged() check in getAccessToken() for why this matters.
    private volatile String cachedForFingerprint;

    public String baseUrl() {
        String env = settingsService.get("dpd_environment", "sandbox");
        return "live".equalsIgnoreCase(env) ? LIVE_BASE_URL : SANDBOX_BASE_URL;
    }

    public String apiKey() {
        String key = settingsService.get("dpd_api_key", "");
        if (key.isBlank()) {
            throw new ValidationException("DPD API key is not configured - set it under Settings > DPD");
        }
        return key;
    }

    /**
     * Drops the cached access/refresh tokens so the next call re-authenticates
     * from scratch with a fresh Basic-auth login, rather than refreshing the
     * existing session. This is what "reset the connection" / "get a new
     * bearer token" means on the DPD side - the bearer token is this cached
     * access token, not the API key/secret (those are the long-lived
     * credentials under Settings > DPD and are untouched by this). Useful
     * when DPD support asks for a fresh token because something about the
     * account/permissions changed and the currently cached one won't reflect
     * that until it naturally expires (up to 24h) or is manually cleared.
     */
    public synchronized void resetConnection() {
        cachedAccessToken = null;
        cachedRefreshToken = null;
        accessTokenExpiresAt = Instant.EPOCH;
        cachedForFingerprint = null;
    }

    /**
     * environment + api key + api secret, joined - a cheap way to tell
     * whether Settings > DPD has changed since the cached token was issued,
     * without keeping a second, longer-lived copy of the secret anywhere.
     */
    private String currentCredentialsFingerprint() {
        String env = settingsService.get("dpd_environment", "sandbox").toLowerCase();
        String key = settingsService.get("dpd_api_key", "");
        String secret = settingsService.get("dpd_api_secret", "");
        return env + "|" + key + "|" + secret;
    }

    /**
     * Returns a currently-valid access token, logging in or refreshing first
     * if needed. Synchronized so two near-simultaneous shipment requests
     * don't both trigger their own login/refresh call.
     */
    public synchronized String getAccessToken() {
        // Bug fixed in v0.113: this cache used to be keyed only on expiry,
        // not on which credentials/environment it was actually issued
        // under. Switching Settings > DPD from sandbox to live (or just
        // pasting in new live key/secret) without a container restart left
        // a still-unexpired token from the OLD environment/credentials
        // being silently reused against the NEW base URL - DPD rejects it,
        // which surfaced as "live services not showing" with everything
        // otherwise configured correctly. Now any credentials/environment
        // change is detected here and forces a fresh login instead of
        // reusing or refreshing the stale token.
        if (cachedAccessToken != null && !currentCredentialsFingerprint().equals(cachedForFingerprint)) {
            log.info("DPD environment/API key/secret changed since the cached token was issued - forcing a fresh login");
            resetConnection();
        }
        if (cachedAccessToken != null && Instant.now().isBefore(accessTokenExpiresAt)) {
            return cachedAccessToken;
        }
        if (cachedRefreshToken != null) {
            try {
                refresh();
                return cachedAccessToken;
            } catch (Exception e) {
                log.warn("DPD token refresh failed, falling back to a fresh login: {}", e.getMessage());
            }
        }
        login();
        return cachedAccessToken;
    }

    private void login() {
        String apiKey = apiKey();
        String apiSecret = settingsService.get("dpd_api_secret", "");
        if (apiSecret.isBlank()) {
            throw new ValidationException("DPD API secret is not configured - set it under Settings > DPD");
        }
        String basicAuth = Base64.getEncoder().encodeToString((apiKey + ":" + apiSecret).getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/v1/customer/auth/access"))
                .header("Authorization", "Basic " + basicAuth)
                .header("Accept", "application/json")
                .GET()
                .build();

        JsonNode body = send(request, "authenticate with DPD");
        applyTokenResponse(body);
    }

    private void refresh() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/v1/customer/auth/refresh"))
                .header("Authorization", "Bearer " + cachedRefreshToken)
                .header("Client-Id", apiKey())
                .header("Accept", "application/json")
                .GET()
                .build();

        JsonNode body = send(request, "refresh DPD token");
        applyTokenResponse(body);
    }

    private void applyTokenResponse(JsonNode body) {
        JsonNode data = body.has("data") ? body.get("data") : body;
        String accessToken = data.path("accessToken").asText(null);
        String refreshToken = data.path("refreshToken").asText(null);
        if (accessToken == null) {
            throw new RuntimeException("DPD auth response did not contain an access token");
        }
        cachedAccessToken = accessToken;
        if (refreshToken != null) {
            cachedRefreshToken = refreshToken;
        }
        accessTokenExpiresAt = Instant.now().plusSeconds(24L * 3600 - EXPIRY_SAFETY_MARGIN_SECONDS);
        cachedForFingerprint = currentCredentialsFingerprint();
    }

    private JsonNode send(HttpRequest request, String actionDescription) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return objectMapper.readTree(response.body());
            }
            log.error("Failed to {} - DPD returned {}: {}", actionDescription, response.statusCode(), response.body());
            throw new ValidationException("Failed to " + actionDescription + " - " + describeAuthError(response.body(), response.statusCode()));
        } catch (java.io.IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Failed to " + actionDescription + ": " + e.getMessage(), e);
        }
    }

    /**
     * DPD's auth error body is {"error": {"statusCode", "error", "message"}} -
     * a different shape from the shipping/collection error format ({"error":
     * [...]})  - surfaces the real reason (e.g. "Failed to validate
     * client-id", "Invalid client secret") instead of a bare HTTP 401, which
     * on its own gives no clue whether the key, the secret, or the
     * sandbox/live environment choice is wrong.
     */
    private String describeAuthError(String responseBody, int statusCode) {
        try {
            JsonNode parsed = objectMapper.readTree(responseBody);
            JsonNode error = parsed.path("error");
            String message = error.path("message").asText(null);
            if (message != null && !message.isBlank()) {
                return "DPD said: " + message
                        + " - check the API key/secret and sandbox/live environment under Settings > DPD";
            }
        } catch (Exception ignored) {
            // fall through to the generic message below
        }
        return "DPD returned HTTP " + statusCode
                + " - check the API key/secret and sandbox/live environment under Settings > DPD";
    }
}
