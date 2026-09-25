package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Manually applying some (or all) of a company's stored credit balance to one invoice. */
public record ApplyCreditBalanceRequest(
        @NotNull Long invoiceId,
        @NotNull @DecimalMin(value = "0.01", message = "Amount must be greater than zero") BigDecimal amount
) {}
