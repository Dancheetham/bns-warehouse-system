package uk.co.bns.warehouse_api.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import uk.co.bns.warehouse_api.service.LoginThrottleService;

import java.io.IOException;

/**
 * Reads {name, password} from a JSON request body rather than form-encoded
 * parameters, which is what UsernamePasswordAuthenticationFilter expects by
 * default - needed since the frontend is a React SPA posting JSON, not an
 * HTML form.
 *
 * Also enforces login throttling (LoginThrottleService): before even trying
 * to authenticate, checks whether this IP or this username currently has to
 * wait out a growing delay from recent failures, and records a fail/success
 * against both keys once the attempt is known. Checking both independently
 * means neither rotating IP nor spraying different usernames from one IP
 * gets around it.
 */
public class JsonLoginFilter extends UsernamePasswordAuthenticationFilter {

    private final ObjectMapper objectMapper;
    private final LoginThrottleService loginThrottle;

    public JsonLoginFilter(ObjectMapper objectMapper, LoginThrottleService loginThrottle) {
        this.objectMapper = objectMapper;
        this.loginThrottle = loginThrottle;
        setFilterProcessesUrl("/api/auth/login");
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) {
        String name;
        String password;
        try {
            JsonNode body = objectMapper.readTree(request.getInputStream());
            name = body.path("name").asText("");
            password = body.path("password").asText("");
        } catch (IOException e) {
            throw new AuthenticationServiceException("Couldn't read login request", e);
        }

        String ipKey = "ip:" + clientIp(request);
        String userKey = "user:" + name.trim().toLowerCase();
        long waitSeconds = Math.max(loginThrottle.secondsRemaining(ipKey), loginThrottle.secondsRemaining(userKey));
        if (waitSeconds > 0) {
            throw new LoginThrottledException("Too many attempts - please wait " + waitSeconds + "s and try again");
        }

        try {
            UsernamePasswordAuthenticationToken authRequest = new UsernamePasswordAuthenticationToken(name, password);
            setDetails(request, authRequest);
            Authentication result = this.getAuthenticationManager().authenticate(authRequest);
            loginThrottle.recordSuccess(ipKey);
            loginThrottle.recordSuccess(userKey);
            return result;
        } catch (AuthenticationException e) {
            loginThrottle.recordFailure(ipKey);
            loginThrottle.recordFailure(userKey);
            throw e;
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
