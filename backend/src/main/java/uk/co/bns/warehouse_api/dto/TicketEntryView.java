package uk.co.bns.warehouse_api.dto;

import java.time.LocalDateTime;

public record TicketEntryView(
        Long id,
        String author,
        String note,
        LocalDateTime createdAt
) {}
