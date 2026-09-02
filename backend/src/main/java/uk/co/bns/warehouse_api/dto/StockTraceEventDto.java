package uk.co.bns.warehouse_api.dto;

import java.time.LocalDateTime;

public record StockTraceEventDto(
        LocalDateTime timestamp,
        String eventType,
        String fromLocation,
        String toLocation,
        String reference,
        String notes,
        String performedBy
) {}
