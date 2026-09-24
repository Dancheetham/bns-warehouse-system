package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import uk.co.bns.warehouse_api.entity.PasswordResetToken;
import uk.co.bns.warehouse_api.entity.User;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.exception.ValidationException;
import uk.co.bns.warehouse_api.repository.PasswordResetTokenRepository;
import uk.co.bns.warehouse_api.repository.UserRepository;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * "Forgot password" - a one-time, one-hour link emailed to whatever address
 * is on file for the account, sent through the shared Settings > Email
 * account (see EmailService). Deliberately silent about whether an account
 * (or an email on that account) actually exists - requestReset() always
 * behaves the same way from the caller's side, successful or not, so this
 * can't be used to enumerate valid usernames/emails on an internet-facing
 * deployment.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration TOKEN_LIFETIME = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SettingsService settingsService;

    // Best-effort fallback for the reset link's base URL, built from
    // whatever the browser actually used to reach the API (see
    // AuthController.baseUrl) - fine for plain LAN access, but request.getScheme()
    // reports "http" even for HTTPS traffic arriving through a reverse proxy
    // or tunnel (nginx/Cloudflare don't forward that here - see nginx.conf).
    // Settings -> Email -> "Public URL" (app_public_url), when set, always
    // wins over this fallback for exactly that reason - set it once for any
    // deployment reachable through anything other than a bare LAN IP.
    @Value("${app.public-url:}")
    private String envPublicUrl;

    public void requestReset(String nameOrEmail, String requestBaseUrl) {
        if (nameOrEmail == null || nameOrEmail.isBlank()) return;

        Optional<User> userOpt = userRepository.findByNameIgnoreCase(nameOrEmail.trim())
                .or(() -> userRepository.findByEmailIgnoreCase(nameOrEmail.trim()));
        if (userOpt.isEmpty()) return;

        User user = userOpt.get();
        if (user.getEmail() == null || user.getEmail().isBlank()) return;

        // A fresh request supersedes any still-live link from an earlier one -
        // only the newest email should actually work.
        tokenRepository.deleteAllByUserIdAndUsedAtIsNull(user.getId());

        String rawToken = generateRawToken();
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.getId());
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(LocalDateTime.now().plus(TOKEN_LIFETIME));
        tokenRepository.save(token);

        String link = publicBaseUrl(requestBaseUrl) + "/reset-password?token=" + rawToken;
        String subject = "BNS Warehouse System - reset your password";
        String body = "Hi " + user.getName() + ",\n\n"
                + "Someone (hopefully you) asked to reset the password for your BNS Warehouse System login.\n\n"
                + "To set a new password, open this link within the next hour:\n"
                + link + "\n\n"
                + "If this wasn't you, you can ignore this email - your password hasn't been changed, and this "
                + "link stops working in an hour either way.";
        emailService.send(user.getEmail(), subject, body);
    }

    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ValidationException("This reset link is invalid - request a new one.");
        }
        PasswordResetToken token = tokenRepository.findByTokenHash(hash(rawToken))
                .filter(t -> t.getUsedAt() == null)
                .filter(t -> t.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new ValidationException("This reset link is invalid or has expired - request a new one."));

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new NotFoundException("User not found"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Mark this one used, and drop any other still-live link for the same
        // user - resetting the password once should retire every outstanding
        // link, not just the one that happened to be clicked.
        token.setUsedAt(LocalDateTime.now());
        tokenRepository.save(token);
        tokenRepository.deleteAllByUserIdAndUsedAtIsNull(user.getId());
    }

    private String publicBaseUrl(String requestBaseUrl) {
        String configured = settingsService.get("app_public_url", envPublicUrl);
        if (configured != null && !configured.isBlank()) {
            return configured.replaceAll("/+$", "");
        }
        return requestBaseUrl;
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(value.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
