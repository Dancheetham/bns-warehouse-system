package uk.co.bns.warehouse_api.dto;

import java.util.List;

public record BinProductGroup(
        Long productId,
        String productSku,
        String productName,
        List<StockItemDetail> items
) {}
