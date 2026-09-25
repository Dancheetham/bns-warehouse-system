package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RecordInvoicePaymentRequest(
        @NotNull Long invoiceId,
        @NotNull @DecimalMin(value = "0.01", message = "Amount must be greater than zero") BigDecimal amount,
        String reference,
        String notes
) {}
