package uk.co.bns.warehouse_api.dto;

import java.util.List;

public record StockTraceResult(
        String identifierType,  // MAC, SERIAL, CARTON, SKU
        String identifier,
        String productSku,
        String productName,
        String currentStatus,
        String currentLocation,
        List<StockTraceEventDto> timeline
) {}
