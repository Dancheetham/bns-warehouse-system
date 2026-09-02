package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record CompanyRequest(
        @NotBlank String name,
        // Null = no credit account for this company.
        BigDecimal creditLimit,
        String shopifyCompanyId,
        String notes
) {}
