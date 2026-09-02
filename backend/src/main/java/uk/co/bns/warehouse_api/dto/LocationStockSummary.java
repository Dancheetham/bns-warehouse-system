package uk.co.bns.warehouse_api.dto;

public record LocationStockSummary(
        Long locationId,
        String locationCode,
        String locationDescription,
        int available,
        int quarantined,
        int allocated,
        int despatched,
        int returned,
        int total
) {}
