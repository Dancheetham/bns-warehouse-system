package uk.co.bns.warehouse_api.dto;

import java.util.List;

public record StockImportResult(
        boolean success,
        int binsCreated,
        int itemsRemoved,
        int itemsCreated,
        int productsSkipped,
        List<String> errors
) {}
