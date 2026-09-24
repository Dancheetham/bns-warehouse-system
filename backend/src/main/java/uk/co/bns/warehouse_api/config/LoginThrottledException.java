package uk.co.bns.warehouse_api.config;

import org.springframework.security.core.AuthenticationException;

/**
 * Thrown by JsonLoginFilter when LoginThrottleService says this IP or
 * username needs to wait before trying again - carried through Spring
 * Security's normal authentication-failure handling so it reaches the same
 * AuthenticationFailureHandler as a wrong password, just with a different
 * message.
 */
public class LoginThrottledException extends AuthenticationException {
    public LoginThrottledException(String msg) {
        super(msg);
    }
}
