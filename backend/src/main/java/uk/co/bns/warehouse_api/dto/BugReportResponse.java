package uk.co.bns.warehouse_api.dto;

import java.time.LocalDateTime;

public record BugReportResponse(
        Long id,
        LocalDateTime occurredAt,
        String source,
        String errorCode,
        String description,
        String context
) {}
