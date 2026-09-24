package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.Email;

public record UpdateEmailRequest(
        // Blank/null clears the email (e.g. to stop "Forgot password?" from
        // working for that login) - only validated as an email shape when
        // actually set, same as CreateUserRequest.email.
        @Email(message = "That doesn't look like a valid email address") String email
) {}
