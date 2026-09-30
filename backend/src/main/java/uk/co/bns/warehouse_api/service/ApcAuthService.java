package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.co.bns.warehouse_api.exception.ValidationException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * APC Overnight's auth is nothing like DPD's OAuth-style token exchange
 * (DpdAuthService) - every request just carries a "remote-user" header of
 * "Basic " + base64(email:password), recomputed fresh from Settings each
 * call. There's no token to cache or expire, so unlike DpdAuthService this
 * has no mutable state at all.
 */
@Service
@RequiredArgsConstructor
public class ApcAuthService {

    private static final String TRAINING_BASE_URL = "https://apc-training.hypaship.com/api/3.0/";
    private static final String LIVE_BASE_URL = "https://apc.hypaship.com/api/3.0/";

    private final SettingsService settingsService;

    public String baseUrl() {
        String env = settingsService.get("apc_environment", "training");
        return "live".equalsIgnoreCase(env) ? LIVE_BASE_URL : TRAINING_BASE_URL;
    }

    /**
     * The "remote-user" header value ("Basic " + base64(email:password)),
     * built fresh from Settings > Couriers > APC on every call - there's
     * nothing to cache, unlike DPD's bearer token.
     */
    public String authHeader() {
        String email = settingsService.get("apc_email", "");
        String password = settingsService.get("apc_password", "");
        if (email.isBlank() || password.isBlank()) {
            throw new ValidationException("APC email/password are not configured - set them under Settings > Couriers > APC");
        }
        String encoded = Base64.getEncoder().encodeToString((email + ":" + password).getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    public String accountNumber() {
        return settingsService.get("apc_account_number", "");
    }
}
