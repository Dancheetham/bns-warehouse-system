package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PickScanRequest(
        @NotNull Long orderLineId,
        @NotBlank String code,
        String pickedBy
) {}
