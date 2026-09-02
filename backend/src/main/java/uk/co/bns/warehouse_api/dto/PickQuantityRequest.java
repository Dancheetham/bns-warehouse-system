package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PickQuantityRequest(
        @NotNull Long orderLineId,
        @Positive int quantity,
        String pickedBy
) {}
