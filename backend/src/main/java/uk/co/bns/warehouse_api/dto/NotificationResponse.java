package uk.co.bns.warehouse_api.dto;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String type,
        String message,
        String link,
        LocalDateTime createdAt,
        boolean read
) {}
