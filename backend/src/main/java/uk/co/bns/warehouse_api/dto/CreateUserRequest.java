package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank String name,
        @NotBlank @Size(min = 6, message = "Password must be at least 6 characters") String password,
        // Optional - only needed for that login to use "Forgot password?" on
        // the login page (see PasswordResetService). A login with no email
        // set simply can't self-serve a reset; someone else with access still
        // can via the "Change password" action in Settings > Users.
        @Email(message = "That doesn't look like a valid email address") String email
) {}
