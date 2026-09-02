package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.NotNull;

public record ReceiveRmaItemInput(
        @NotNull Long rmaItemId,
        boolean received,
        // Only meaningful for non-faulty items that failed the resale checks.
        boolean rsfApplied,
        boolean grandstreamWarrantyChecked
) {}
