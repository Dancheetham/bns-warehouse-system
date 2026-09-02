package uk.co.bns.warehouse_api.dto;

import java.time.LocalDateTime;

public record ApiKeySummary(
        Long id,
        String label,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime lastUsedAt
) {}
