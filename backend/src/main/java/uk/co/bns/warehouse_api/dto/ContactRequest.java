package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ContactRequest(
        @NotNull Long companyId,
        @NotBlank String name,
        String email,
        String phone,
        String position,
        Boolean mainContact,
        Boolean active
) {}
