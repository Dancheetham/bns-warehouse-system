package uk.co.bns.warehouse_api.dto;

public record PackedItemView(
        Long stockItemId,
        String sku,
        String productName,
        // MAC/serial for tracked items, batch code if that's how it was scanned, or
        // "(unit)" for NONE-tracking products with no scannable identifier at all.
        String identifier,
        Long cartonId
) {}
