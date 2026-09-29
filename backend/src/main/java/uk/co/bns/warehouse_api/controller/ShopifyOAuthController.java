package uk.co.bns.warehouse_api.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.service.ShopifyOAuthService;
import uk.co.bns.warehouse_api.service.SettingsService;

import java.net.URI;

/**
 * The "Connect to Shopify" handshake.
 *
 * Fixed in v0.115: this used to be reached directly on the API's own
 * hardcoded port (8080), bypassing the nginx-proxied frontend origin
 * entirely, on the theory that hitting the API's own port kept
 * request.getServerName()/getServerPort() an exact match for whatever host
 * the browser used. That reasoning breaks down for anything other than a
 * bare LAN IP with both ports directly reachable - behind a reverse proxy,
 * Cloudflare Tunnel, or a custom domain (only the frontend's port/hostname
 * is actually exposed externally; the API's own port typically isn't
 * reachable at all, which is exactly what "site can't be reached" on
 * :8080 meant), and it also silently broke again the moment v0.107 made
 * the frontend port configurable, since frontendUrl() below still
 * hardcoded :8081.
 *
 * Now everything goes through the same origin as the frontend (nginx's
 * /api/ proxy - see nginx.conf), same as every other API call the frontend
 * makes, and the actual external base URL is resolved the same way
 * PasswordResetService already does it: Settings > Email > "Public URL"
 * (app_public_url) wins when set, since request.getScheme() reports "http"
 * even for HTTPS traffic arriving through a reverse proxy or tunnel (nginx
 * doesn't forward that here). Falling back to guessing from the request
 * itself only when Public URL is left blank - fine for a bare LAN
 * deployment, where the guess and reality are the same thing.
 */
@RestController
@RequestMapping("/api/shopify/oauth")
@RequiredArgsConstructor
public class ShopifyOAuthController {

    private final ShopifyOAuthService shopifyOAuthService;
    private final SettingsService settingsService;

    @Value("${app.public-url:}")
    private String envPublicUrl;

    @GetMapping("/start")
    public ResponseEntity<Void> start(HttpServletRequest request) {
        String redirectUri = resolvedBaseUrl(request) + "/api/shopify/oauth/callback";
        String authorizeUrl = shopifyOAuthService.buildAuthorizeUrl(redirectUri);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, authorizeUrl)
                .build();
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam String shop, @RequestParam String code, @RequestParam String state,
            HttpServletRequest request) {
        String baseUrl = resolvedBaseUrl(request);
        String landingUrl;
        try {
            shopifyOAuthService.handleCallback(shop, code, state);
            landingUrl = baseUrl + "/shopify-sync?connected=true";
        } catch (Exception e) {
            landingUrl = baseUrl + "/shopify-sync?connected=false&error="
                    + java.net.URLEncoder.encode(e.getMessage(), java.nio.charset.StandardCharsets.UTF_8);
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, landingUrl)
                .build();
    }

    @PostMapping("/disconnect")
    public void disconnect() {
        shopifyOAuthService.disconnect();
    }

    /**
     * Same resolution order as PasswordResetService: Settings > Email >
     * "Public URL" first (trimmed of any trailing slash), then the
     * app.public-url env var, then a best-effort guess from the request
     * itself - which, now that /start and /callback are reached through
     * the same nginx-proxied origin as everything else, is one single
     * frontend-facing base URL rather than two different hardcoded ports.
     */
    private String resolvedBaseUrl(HttpServletRequest request) {
        String configured = settingsService.get("app_public_url", envPublicUrl);
        if (configured != null && !configured.isBlank()) {
            String trimmed = configured.trim();
            return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        }
        URI uri = URI.create(request.getRequestURL().toString());
        int port = uri.getPort();
        return uri.getScheme() + "://" + uri.getHost() + (port > 0 ? ":" + port : "");
    }
}
