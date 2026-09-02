package uk.co.bns.warehouse_api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.dto.AuthUserView;
import uk.co.bns.warehouse_api.entity.User;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.repository.UserRepository;

/**
 * /login and /logout themselves are handled directly by SecurityConfig's
 * filter chain (JsonLoginFilter / the logout handler) - this controller only
 * covers "who am I right now", which the frontend polls on load to decide
 * whether to show the app or the login page.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;

    @GetMapping("/me")
    public AuthUserView me(Authentication authentication) {
        // Reaching this method at all means Spring Security already let the
        // request through, i.e. the caller is authenticated - an unauthenticated
        // call never gets here (see SecurityConfig's authenticationEntryPoint).
        User user = userRepository.findByName(authentication.getName())
                .orElseThrow(() -> new NotFoundException("User not found"));
        return new AuthUserView(user.getId(), user.getName());
    }
}
