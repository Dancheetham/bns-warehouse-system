package uk.co.bns.warehouse_api.dto;

import java.math.BigDecimal;

public record GeneratedInvoiceSummary(
        Long invoiceId,
        Integer invoiceNumber,
        String companyName,
        BigDecimal grandTotal,
        boolean emailed,
        String emailError
) {}
