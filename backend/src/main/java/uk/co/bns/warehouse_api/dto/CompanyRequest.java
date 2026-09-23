package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record CompanyRequest(
        @NotBlank String name,
        // Null = no credit account for this company.
        BigDecimal creditLimit,
        String shopifyCompanyId,
        String notes,
        // Sent as the DPD customs importer's EORI/VAT number when this
        // company's orders ship to a customs country (e.g. Ireland) - see
        // Company.java.
        String eoriNumber,
        String vatNumber,
        // The OrderWise "Account number" code - see Company.java.
        String accountNumber,
        // Credit hold - see Company.onHold.
        Boolean onHold,
        Boolean doNotUse,
        Boolean gaps,
        Boolean gdms
) {}
