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

    // Domain depends on which region the BNS GDMS account is registered in
    // (Settings > GDMS > Region), US (www.gdms.cloud) or EU (eu.gdms.cloud).
    // Defaults to EU since BNS is a UK distributor.
    private String domain() {
        String region = settingsService.get("gdms_region", "eu").toLowerCase();
        return "us".equals(region) ? "www.gdms.cloud" : "eu.gdms.cloud";
    }

    /**
     * https://{gdms_domain}/oapi/{version} - for every Channel Management
     * (and other business) call. {version} is the literal string "1.0.0",
     * per the doc's own sample request URLs (e.g.
     * ".../oapi/{version}/channel/sub/list") - NOT "v1.0.0", which is what
     * this originally used and, combined with the missing "/channel" prefix
     * GdmsChannelService has separately been fixed to add, meant every
     * Channel Management call was hitting a URL that simply didn't exist.
     */
    public String baseUrl() {
        return "https://" + domain() + "/oapi/1.0.0";
    }

    /**
     * https://{gdms_domain}/oapi - the Getting Token/Refreshing Token calls
     * are NOT under the versioned path (the doc's own sample request URL for
     * /oauth/token is ".../oapi/oauth/token", no {version} segment at all) -
     * this was also being sent under the versioned baseUrl() above, which
     * (compounding the wrong version string) meant login never even reached
     * a real endpoint, and is the most likely cause of the generic Spring
     * Security "Full authentication is required to access this resource"
     * 401 - that's the default response for a request that doesn't match
     * any mapped, unauthenticated endpoint.
     */
    private String authBaseUrl() {
        return "https://" + domain() + "/oapi";
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
        String clientId = clientId();
        String clientSecret = clientSecret();
        long timestamp = Instant.now().toEpochMilli();

        // Unlike every other endpoint, Getting Token signs its OWN request
        // params (there's no access_token yet to sign instead) - see
        // GdmsSignatureUtil.calculateSignature(SortedMap, String) for the
        // full reasoning.
        java.util.SortedMap<String, String> signedParams = new java.util.TreeMap<>();
        signedParams.put("username", username);
        signedParams.put("password", encodedPassword);
        signedParams.put("grant_type", "password");
        signedParams.put("client_id", clientId);
        signedParams.put("client_secret", clientSecret);
        signedParams.put("timestamp", String.valueOf(timestamp));
        String signature = GdmsSignatureUtil.calculateSignature(signedParams, null);

        String query = "grant_type=password"
                + "&username=" + encode(username)
                + "&password=" + encode(encodedPassword)
                + "&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&timestamp=" + timestamp
                + "&signature=" + signature;

        HttpRequest request = HttpRequest.newBuilder(URI.create(authBaseUrl() + "/oauth/token?" + query))
                .header("Accept", "application/json")
                .GET()
                .build();

        JsonNode body = send(request, "authenticate with GDMS");
        applyTokenResponse(body);
    }

    private void refresh() {
        String clientId = clientId();
        String clientSecret = clientSecret();
        long timestamp = Instant.now().toEpochMilli();

        java.util.SortedMap<String, String> signedParams = new java.util.TreeMap<>();
        signedParams.put("refresh_token", cachedRefreshToken);
        signedParams.put("grant_type", "refresh_token");
        signedParams.put("client_id", clientId);
        signedParams.put("client_secret", clientSecret);
        signedParams.put("timestamp", String.valueOf(timestamp));
        String signature = GdmsSignatureUtil.calculateSignature(signedParams, null);

        String query = "grant_type=refresh_token"
                + "&refresh_token=" + encode(cachedRefreshToken)
                + "&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&timestamp=" + timestamp
                + "&signature=" + signature;

        HttpRequest request = HttpRequest.newBuilder(URI.create(authBaseUrl() + "/oauth/token?" + query))
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
            // GDMS can return HTTP 200 with an error body (bad credentials,
            // wrong client_id/secret, wrong region, etc.) rather than a
            // non-2xx status - send() only catches the latter, so this is
            // the only place that ever sees the former. Without this, every
            // credentials problem surfaced as the unhelpful "did not contain
            // an access token" with no indication of what was actually
            // wrong - surface GDMS's own message (or the raw body, if it
            // didn't send one) instead.
            throw new ValidationException("GDMS didn't return an access token - " + describeAuthError(body.toString(), 200));
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
            if (message == null || message.isBlank()) {
                message = parsed.path("error").asText(null);
            }
            if (message != null && !message.isBlank()) {
                return "GDMS said: " + message + " - check the client ID/secret and username/password under Settings > GDMS";
            }
        } catch (Exception ignored) {
            // fall through to the generic message below
        }
        // None of the usual message fields were present - better to show the
        // raw response than nothing, so this doesn't have to be chased
        // through the container logs every time.
        return "GDMS returned HTTP " + statusCode + " with no recognisable error message - raw response: "
                + truncate(responseBody, 500)
                + " - check the client ID/secret and username/password under Settings > GDMS";
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max) + "…" : value;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
