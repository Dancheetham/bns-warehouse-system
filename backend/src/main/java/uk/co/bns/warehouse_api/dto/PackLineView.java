package uk.co.bns.warehouse_api.dto;

public record PackLineView(
        Long cartonLineId,
        Long orderLineId,
        String sku,
        String productName,
        int quantity,
        Long cartonId
) {}
