package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.NotNull;

public record AssignCartonLineRequest(
        @NotNull Long cartonLineId,
        // Null means "unassign" - send back to the unassigned pool.
        Long cartonId
) {}
