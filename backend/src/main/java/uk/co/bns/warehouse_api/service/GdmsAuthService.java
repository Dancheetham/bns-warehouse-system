package uk.co.bns.warehouse_api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.co.bns.warehouse_api.exception.ValidationException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * GDMS's own auth flow: OAuth 2.0 password grant (GET /oauth/token with
 * username/password/client_id/client_secret) returning an access_token +
 * refresh_token pair, cached in-memory the same way DpdAuthService caches
 * DPD's bearer token - see that class for the reasoning behind the
 * credentials-fingerprint check (forces a fresh login if Settings > GDMS
 * changes, rather than silently reusing a token issued under the old
 * credentials).
 *
 * Unlike DPD, GDMS ALSO requires a fresh HMAC-style signature on every
 * individual API call (see GdmsSignatureUtil) - this service only handles
 * the token half; GdmsChannelService builds the per-request signature using
 * whatever token this returns.
 */
@Service
@RequiredArgsConstructor
public class GdmsAuthService {

    private static final Logger log = LoggerFactory.getLogger(GdmsAuthService.class);

    // Refresh a couple of minutes early rather than racing actual expiry.
    private static final long EXPIRY_SAFETY_MARGIN_SECONDS = 120;

    private final SettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private volatile String cachedAccessToken;
    private volatile String cachedRefreshToken;
    private volatile Instant accessTokenExpiresAt = Instant.EPOCH;
    private volatile String cachedForFingerprint;

    /**
     * https://{gdms_domain}/oapi/{version} - domain depends on which region
     * the BNS GDMS account is registered in (Settings > GDMS > Region), US
     * (www.gdms.cloud) or EU (eu.gdms.cloud). Defaults to EU since BNS is a
     * UK distributor.
     */
    public String baseUrl() {
        String region = settingsService.get("gdms_region", "eu").toLowerCase();
        String domain = "us".equals(region) ? "www.gdms.cloud" : "eu.gdms.cloud";
        return "https://" + domain + "/oapi/v1.0.0";
    }

    public String clientId() {
        String id = settingsService.get("gdms_client_id", "");
        if (id.isBlank()) {
            throw new ValidationException("GDMS client ID is not configured - set it under Settings > GDMS");
        }
        return id;
    }

    public String clientSecret() {
        String secret = settingsService.get("gdms_client_secret", "");
        if (secret.isBlank()) {
            throw new ValidationException("GDMS client secret is not configured - set it under Settings > GDMS");
        }
        return secret;
    }

    /** Drops the cached tokens so the next call logs in fresh. Same "reset connection" idea as DPD's. */
    public synchronized void resetConnection() {
        cachedAccessToken = null;
        cachedRefreshToken = null;
        accessTokenExpiresAt = Instant.EPOCH;
        cachedForFingerprint = null;
    }

    private String currentCredentialsFingerprint() {
        String region = settingsService.get("gdms_region", "eu").toLowerCase();
        String clientId = settingsService.get("gdms_client_id", "");
        String clientSecret = settingsService.get("gdms_client_secret", "");
        String username = settingsService.get("gdms_username", "");
        String password = settingsService.get("gdms_password", "");
        return region + "|" + clientId + "|" + clientSecret + "|" + username + "|" + password;
    }

    public synchronized String getAccessToken() {
        if (cachedAccessToken != null && !currentCredentialsFingerprint().equals(cachedForFingerprint)) {
            log.info("GDMS credentials changed since the cached token was issued - forcing a fresh login");
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
                log.warn("GDMS token refresh failed, falling back to a fresh login: {}", e.getMessage());
            }
        }
        login();
        return cachedAccessToken;
    }

    private void login() {
        String username = settingsService.get("gdms_username", "");
        String password = settingsService.get("gdms_password", "");
        if (username.isBlank() || password.isBlank()) {
            throw new ValidationException("GDMS username/password is not configured - set it under Settings > GDMS");
        }
        String encodedPassword = GdmsSignatureUtil.encodePasswordForToken(password);

        String query = "grant_type=password"
                + "&username=" + encode(username)
                + "&password=" + encode(encodedPassword)
                + "&client_id=" + encode(clientId())
                + "&client_secret=" + encode(clientSecret());

        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/oauth/token?" + query))
                .header("Accept", "application/json")
                .GET()
                .build();

        JsonNode body = send(request, "authenticate with GDMS");
        applyTokenResponse(body);
    }

    private void refresh() {
        String query = "grant_type=refresh_token"
                + "&refresh_token=" + encode(cachedRefreshToken)
                + "&client_id=" + encode(clientId())
                + "&client_secret=" + encode(clientSecret());

        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/oauth/token?" + query))
                .header("Accept", "application/json")
                .GET()
                .build();

        JsonNode body = send(request, "refresh GDMS token");
        applyTokenResponse(body);
    }

    private void applyTokenResponse(JsonNode body) {
        // GDMS's other endpoints wrap their payload in {"data": {...}}, but
        // /oauth/token is a standard OAuth2 token endpoint, so this handles
        // both shapes rather than assuming one - same defensive approach as
        // DpdAuthService.applyTokenResponse.
        JsonNode data = body.has("data") ? body.get("data") : body;
        String accessToken = data.path("access_token").asText(null);
        String refreshToken = data.path("refresh_token").asText(null);
        long expiresIn = data.path("expires_in").asLong(3600);
        if (accessToken == null) {
            throw new RuntimeException("GDMS auth response did not contain an access token");
        }
        cachedAccessToken = accessToken;
        if (refreshToken != null) {
            cachedRefreshToken = refreshToken;
        }
        accessTokenExpiresAt = Instant.now().plusSeconds(Math.max(expiresIn - EXPIRY_SAFETY_MARGIN_SECONDS, 30));
        cachedForFingerprint = currentCredentialsFingerprint();
    }

    private JsonNode send(HttpRequest request, String actionDescription) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return objectMapper.readTree(response.body());
            }
            log.error("Failed to {} - GDMS returned {}: {}", actionDescription, response.statusCode(), response.body());
            throw new ValidationException("Failed to " + actionDescription + " - " + describeAuthError(response.body(), response.statusCode()));
        } catch (java.io.IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Failed to " + actionDescription + ": " + e.getMessage(), e);
        }
    }

    private String describeAuthError(String responseBody, int statusCode) {
        try {
            JsonNode parsed = objectMapper.readTree(responseBody);
            String message = parsed.path("msg").asText(null);
            if (message == null || message.isBlank()) {
                message = parsed.path("error_description").asText(null);
            }
            if (message != null && !message.isBlank()) {
                return "GDMS said: " + message + " - check the client ID/secret and username/password under Settings > GDMS";
            }
        } catch (Exception ignored) {
            // fall through to the generic message below
        }
        return "GDMS returned HTTP " + statusCode + " - check the client ID/secret and username/password under Settings > GDMS";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
