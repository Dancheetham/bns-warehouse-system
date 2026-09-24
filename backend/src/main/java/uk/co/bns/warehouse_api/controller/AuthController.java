package uk.co.bns.warehouse_api.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.dto.AuthUserView;
import uk.co.bns.warehouse_api.dto.ForgotPasswordRequest;
import uk.co.bns.warehouse_api.dto.ResetPasswordRequest;
import uk.co.bns.warehouse_api.entity.User;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.repository.UserRepository;
import uk.co.bns.warehouse_api.service.PasswordResetService;
import uk.co.bns.warehouse_api.service.SimpleRateLimiter;

import java.net.URI;
import java.time.Duration;

/**
 * /login and /logout themselves are handled directly by SecurityConfig's
 * filter chain (JsonLoginFilter / the logout handler) - this controller
 * covers "who am I right now" (which the frontend polls on load to decide
 * whether to show the app or the login page) and the two unauthenticated
 * password-reset steps (see PasswordResetService for the actual logic -
 * this controller is deliberately thin, just request plumbing + rate limiting).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordResetService passwordResetService;
    private final SimpleRateLimiter rateLimiter;

    @GetMapping("/me")
    public AuthUserView me(Authentication authentication) {
        // Reaching this method at all means Spring Security already let the
        // request through, i.e. the caller is authenticated - an unauthenticated
        // call never gets here (see SecurityConfig's authenticationEntryPoint).
        User user = userRepository.findByName(authentication.getName())
                .orElseThrow(() -> new NotFoundException("User not found"));
        return new AuthUserView(user.getId(), user.getName(), user.getEmail());
    }

    // Unauthenticated by design (see SecurityConfig's permitAll list) - has to
    // be reachable by someone who is, by definition, currently locked out.
    // Always responds the same way whether or not the given name/email
    // actually matches anything (see PasswordResetService), and is rate
    // limited per client IP so it can't be used to mail-bomb an inbox or as
    // a timing side-channel for guessing valid logins.
    @PostMapping("/forgot-password")
    public void forgotPassword(@RequestBody ForgotPasswordRequest request, HttpServletRequest servletRequest) {
        if (rateLimiter.allow("forgot-password:" + clientIp(servletRequest), 5, Duration.ofMinutes(15))) {
            passwordResetService.requestReset(request.usernameOrEmail(), baseUrl(servletRequest));
        }
        // Deliberately no branch, no error, nothing different for the
        // rate-limited case either - the response is identical regardless.
    }

    // Also unauthenticated by design - the whole point is setting a new
    // password without being logged in. PasswordResetService itself rejects
    // an invalid/expired/already-used token, so no separate rate limit is
    // needed here (a wrong guess at a 32-byte random token isn't something
    // brute-forcing threatens in any practical way).
    @PostMapping("/reset-password")
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // First entry is the original client - anything after that was
            // added by proxies/tunnels further down the chain (nginx, Cloudflare).
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String baseUrl(HttpServletRequest request) {
        URI uri = URI.create(request.getRequestURL().toString());
        int port = uri.getPort();
        return uri.getScheme() + "://" + uri.getHost() + (port > 0 ? ":" + port : "");
    }
}
