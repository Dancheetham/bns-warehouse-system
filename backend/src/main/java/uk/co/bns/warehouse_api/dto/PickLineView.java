package uk.co.bns.warehouse_api.dto;

public record PickLineView(
        Long orderLineId,
        Long productId,
        String sku,
        String productName,
        String defaultBinCode,
        int defaultBinAvailable,
        int totalAvailable,
        int quantityOrdered,
        int quantityPicked,
        boolean requiresScan,
        boolean complete,
        boolean shortPicked
) {}
