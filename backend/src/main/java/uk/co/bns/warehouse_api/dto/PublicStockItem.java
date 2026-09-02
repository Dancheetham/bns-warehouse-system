package uk.co.bns.warehouse_api.dto;

public record PublicStockItem(
        String sku,
        String name,
        int availableQuantity
) {}
