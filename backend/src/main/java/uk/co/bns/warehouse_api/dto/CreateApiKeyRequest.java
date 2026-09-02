package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateApiKeyRequest(
        @NotBlank String label
) {}
