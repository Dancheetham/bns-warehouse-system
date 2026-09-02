package uk.co.bns.warehouse_api.dto;

import java.time.LocalDateTime;

public record ApiKeyCreatedResponse(
        Long id,
        String label,
        String apiKey,
        LocalDateTime createdAt
) {}
