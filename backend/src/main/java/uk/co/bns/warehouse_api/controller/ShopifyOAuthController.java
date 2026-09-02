package uk.co.bns.warehouse_api.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.service.ShopifyOAuthService;

import java.net.URI;

/**
 * The "Connect to Shopify" handshake. Deliberately reached directly on the API's
 * own port (see docker-compose.yml/README) rather than through the nginx-proxied
 * frontend origin - that keeps request.getServerName()/getServerPort() an exact,
 * unambiguous match for whatever host the browser used to start the flow
 * (localhost vs a LAN IP), which is what has to be sent to Shopify as
 * redirect_uri and match one of the app's registered redirect URLs exactly.
 */
@RestController
@RequestMapping("/api/shopify/oauth")
@RequiredArgsConstructor
public class ShopifyOAuthController {

    private final ShopifyOAuthService shopifyOAuthService;

    @GetMapping("/start")
    public ResponseEntity<Void> start(HttpServletRequest request) {
        String redirectUri = baseUrl(request) + "/api/shopify/oauth/callback";
        String authorizeUrl = shopifyOAuthService.buildAuthorizeUrl(redirectUri);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, authorizeUrl)
                .build();
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam String shop, @RequestParam String code, @RequestParam String state,
            HttpServletRequest request) {
        String landingUrl;
        try {
            shopifyOAuthService.handleCallback(shop, code, state);
            landingUrl = frontendUrl(request) + "/shopify-sync?connected=true";
        } catch (Exception e) {
            landingUrl = frontendUrl(request) + "/shopify-sync?connected=false&error="
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

    private String baseUrl(HttpServletRequest request) {
        URI uri = URI.create(request.getRequestURL().toString());
        int port = uri.getPort();
        return uri.getScheme() + "://" + uri.getHost() + (port > 0 ? ":" + port : "");
    }

    // The API's own port is 8080; the frontend (nginx) is always 8081 in this
    // project's docker-compose - swap the port rather than guess a different host.
    private String frontendUrl(HttpServletRequest request) {
        URI uri = URI.create(request.getRequestURL().toString());
        return uri.getScheme() + "://" + uri.getHost() + ":8081";
    }
}
