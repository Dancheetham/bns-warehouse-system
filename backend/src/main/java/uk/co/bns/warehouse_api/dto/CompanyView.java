package uk.co.bns.warehouse_api.dto;

import java.math.BigDecimal;

public record CompanyView(
        Long id,
        String name,
        BigDecimal creditLimit,
        String shopifyCompanyId,
        String notes,
        // Only present when creditLimit is set - the running total of unpaid
        // order value, matching the OrderWise "amount owing" figure.
        BigDecimal creditUsed,
        BigDecimal creditAvailable,
        boolean overLimit
) {}
