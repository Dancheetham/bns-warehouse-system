package uk.co.bns.warehouse_api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import java.util.Map;

/**
 * Session-cookie based auth (not JWT) - this is a single-instance app behind
 * nginx on a LAN, not a distributed/stateless deployment, so a plain HTTP
 * session is the simplest thing that's actually correct here. Sessions are
 * long-lived by default (see application.yml) rather than expiring quickly -
 * these are shared warehouse-floor devices used all shift, not public kiosks.
 *
 * CSRF is disabled - deliberately, for the same reason the public RMA
 * endpoint's abuse-protection gap was flagged rather than silently accepted:
 * this app is LAN-only today. Revisit if this is ever exposed to the
 * internet, at which point CSRF protection (and the public RMA endpoint's
 * rate limiting) both need real attention together.
 *
 * The public RMA form and the Shopify OAuth callback stay unauthenticated -
 * customers submitting a return obviously have no login, and Shopify's
 * browser-redirect callback can't carry our session cookie since it's a
 * fresh, unauthenticated navigation from Shopify's own domain.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
        // AbstractAuthenticationProcessingFilter (which our JsonLoginFilter
        // extends) holds its OWN securityContextRepository, independent of
        // whatever the rest of the chain is configured with - it defaults to
        // request-attribute-only storage (valid for a single request only),
        // never actually persisting anything to the session. Without this
        // explicit wiring, login looked successful for the one request it
        // happened in, then evaporated immediately - nothing was ever saved
        // for the next request to find.
        SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

        JsonLoginFilter loginFilter = new JsonLoginFilter(objectMapper);
        loginFilter.setAuthenticationManager(authenticationManager);
        loginFilter.setSecurityContextRepository(securityContextRepository);
        loginFilter.setAuthenticationSuccessHandler((request, response, authentication) -> {
            response.setStatus(HttpStatus.OK.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), Map.of("name", authentication.getName()));
        });
        loginFilter.setAuthenticationFailureHandler((request, response, exception) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), Map.of("error", "Incorrect name or password"));
        });

        http
                // No explicit CorsConfigurationSource bean here - Spring Security
                // picks up the existing WebMvcConfigurer-based CORS mapping
                // (WebConfig.addCorsMappings) automatically via this call.
                .cors(org.springframework.security.config.Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/logout").permitAll()
                        .requestMatchers("/api/rma-requests/**").permitAll()
                        .requestMatchers("/api/shopify/oauth/**").permitAll()
                        // Its own separate SHA-256 API-key check happens after this, via
                        // ApiKeyInterceptor - this just lets the request past the session
                        // gate so that mechanism (unrelated to logins) keeps working.
                        .requestMatchers("/api/public/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(HttpStatus.OK.value())))
                .addFilterAt(loginFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
