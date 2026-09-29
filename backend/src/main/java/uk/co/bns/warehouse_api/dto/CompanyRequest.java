package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import uk.co.bns.warehouse_api.enums.InvoiceGrouping;

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
        Boolean gdms,
        // Which GDMS channel this company's despatched devices go to - see
        // Company.java. gdmsChannelId is the raw ID from GDMS's own
        // /sub/list; gdmsChannelName is carried alongside it purely for
        // display.
        String gdmsChannelId,
        String gdmsChannelName,
        // Generate Invoices - see Company.java.
        @Email(message = "That doesn't look like a valid email address") String invoiceEmail,
        BigDecimal vatRate,
        InvoiceGrouping invoiceGrouping,
        // Payment Tracking - see Company.java. Null paymentTermsDays uses the
        // global default (Settings > Invoicing).
        Integer paymentTermsDays,
        Boolean autoHoldOnOverdue
) {}
