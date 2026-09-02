package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.NotNull;

public record AssignCartonItemRequest(
        @NotNull Long stockItemId,
        // Null means "unassign" - send back to the unassigned pool.
        Long cartonId
) {}
