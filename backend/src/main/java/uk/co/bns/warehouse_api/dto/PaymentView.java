package uk.co.bns.warehouse_api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentView(
        Long id,
        Long orderId,
        String orderNumber,
        BigDecimal amount,
        LocalDateTime receivedAt,
        String reference,
        String notes,
        String recordedBy
) {}
