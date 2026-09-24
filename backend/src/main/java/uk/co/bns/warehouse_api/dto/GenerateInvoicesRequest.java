package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import uk.co.bns.warehouse_api.enums.InvoiceType;

import java.time.LocalDate;
import java.util.List;

public record GenerateInvoicesRequest(
        @NotNull InvoiceType invoiceType,
        @NotNull LocalDate generationDate,
        @NotEmpty List<Long> orderLineIds
) {}
