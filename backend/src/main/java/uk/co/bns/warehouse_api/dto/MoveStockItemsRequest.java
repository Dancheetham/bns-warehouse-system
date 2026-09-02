package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record MoveStockItemsRequest(
        @NotEmpty List<Long> stockItemIds,
        @NotNull Long toLocationId,
        String movedBy,
        String notes
) {}
