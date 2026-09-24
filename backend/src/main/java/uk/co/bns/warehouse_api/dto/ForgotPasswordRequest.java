package uk.co.bns.warehouse_api.dto;

/**
 * Deliberately just a free-text field, not @NotBlank/@Email - accepts either
 * a username or an email address (PasswordResetService tries both), and an
 * invalid-looking value here should behave identically to a valid-looking
 * one that just doesn't match anything, so this endpoint can't be used to
 * fingerprint which usernames/emails exist.
 */
public record ForgotPasswordRequest(String usernameOrEmail) {}
