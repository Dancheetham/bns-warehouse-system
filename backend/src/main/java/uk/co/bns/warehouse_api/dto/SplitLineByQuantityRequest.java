package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// Splits a line into equal-sized boxes of `boxSize`, with any remainder as a final
// shorter line. E.g. 32 required, split by quantity 8 -> four lines of 8.
public record SplitLineByQuantityRequest(
        @NotNull Long cartonLineId,
        @Positive int boxSize
) {}
