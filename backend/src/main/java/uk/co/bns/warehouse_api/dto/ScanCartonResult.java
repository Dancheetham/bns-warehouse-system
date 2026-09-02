package uk.co.bns.warehouse_api.dto;

public record ScanCartonResult(
        String status,      // ADDED, ALREADY_IN_SESSION, ALREADY_RECEIVED, NOT_FOUND
        String message,
        String productSku,
        String productName,
        Integer itemCount
) {}
