package uk.co.bns.warehouse_api.dto;

import java.util.List;

public record StockImportPreview(
        int totalRows,
        List<String> binsToCreate,
        int matchedProductCount,
        List<UnmatchedSkuSummary> unmatchedSkus,
        List<TrackingTypeChange> trackingTypeChanges,
        int itemsToCreate,
        int currentOnHandItemsToRemove,
        List<String> edgeCaseNotes,
        List<String> errors
) {}
