package uk.co.bns.warehouse_api.dto;

import java.util.List;

public record GenerateInvoicesResult(
        List<GeneratedInvoiceSummary> invoices,
        List<String> warnings
) {}
