package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentRequest(
        @NotNull @Positive BigDecimal amount,
        LocalDateTime receivedAt,
        String reference,
        String notes,
        String recordedBy
) {}
