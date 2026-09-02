package uk.co.bns.warehouse_api.dto;

public record StockItemDetail(
        Long id,
        String macAddress,
        String serialNumber,
        String wifiMacAddress,
        String batchCode,
        String status,
        String locationCode
) {}
