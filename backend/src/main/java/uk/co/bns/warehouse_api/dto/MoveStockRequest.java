package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record MoveStockRequest(
        @NotNull Long fromLocationId,
        @NotNull Long toLocationId,
        @Positive Integer quantity,
        String movedBy,
        String notes
) {}
