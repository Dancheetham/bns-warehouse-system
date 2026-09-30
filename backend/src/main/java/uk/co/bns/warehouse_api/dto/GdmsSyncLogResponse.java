package uk.co.bns.warehouse_api.dto;

import java.time.LocalDateTime;

public record GdmsSyncLogResponse(
        Long id,
        LocalDateTime attemptedAt,
        String operation,
        String source,
        String orderNumber,
        String macAddress,
        String channelId,
        String channelName,
        String status,
        String errorReason
) {}
