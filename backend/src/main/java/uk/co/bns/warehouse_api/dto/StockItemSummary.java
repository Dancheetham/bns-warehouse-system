package uk.co.bns.warehouse_api.dto;

public record StockItemSummary(
        Long id,
        String macAddress,
        String serialNumber,
        String wifiMacAddress,
        String batchCode,
        String defaultPassword,
        String productSku,
        String productName,
        Long locationId,
        String locationCode,
        String status
) {}
