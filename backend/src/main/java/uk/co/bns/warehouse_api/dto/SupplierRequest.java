package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.NotBlank;

public record SupplierRequest(
        @NotBlank String name,
        String accountNumber,
        String contactName,
        String contactEmail,
        String contactPhone
) {}
