package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// Splits a line into two: one of `amount`, one of whatever's left. E.g. 32 required,
// split by 30 -> a line of 30 and a line of 2.
public record SplitLineRequest(
        @NotNull Long cartonLineId,
        @Positive int amount
) {}
