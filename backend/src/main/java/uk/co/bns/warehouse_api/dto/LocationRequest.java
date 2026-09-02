package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.NotBlank;

public record LocationRequest(
        @NotBlank String code,
        String description
) {}
