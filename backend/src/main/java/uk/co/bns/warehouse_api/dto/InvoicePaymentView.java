package uk.co.bns.warehouse_api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InvoicePaymentView(
        Long id,
        BigDecimal amount,
        LocalDateTime receivedAt,
        String reference,
        String notes,
        String recordedBy,
        boolean fromCreditBalance
) {}
