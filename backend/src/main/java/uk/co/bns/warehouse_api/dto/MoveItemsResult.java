package uk.co.bns.warehouse_api.dto;

import java.util.List;

public record MoveItemsResult(
        int movedCount,
        int skippedCount,
        List<String> skippedReasons
) {}
