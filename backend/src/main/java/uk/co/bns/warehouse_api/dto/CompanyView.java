package uk.co.bns.warehouse_api.dto;

import uk.co.bns.warehouse_api.enums.InvoiceGrouping;

import java.math.BigDecimal;

public record CompanyView(
        Long id,
        String name,
        BigDecimal creditLimit,
        String shopifyCompanyId,
        String notes,
        String eoriNumber,
        String vatNumber,
        String accountNumber,
        boolean onHold,
        boolean doNotUse,
        boolean gaps,
        boolean gdms,
        // Only present when creditLimit is set - the running total of unpaid
        // order value, matching the OrderWise "amount owing" figure.
        BigDecimal creditUsed,
        BigDecimal creditAvailable,
        boolean overLimit,
        // Generate Invoices - see Company.java.
        String invoiceEmail,
        BigDecimal vatRate,
        InvoiceGrouping invoiceGrouping
) {}
