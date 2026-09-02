package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import uk.co.bns.warehouse_api.enums.TrackingType;

public record ProductRequest(
        @NotBlank String sku,
        @NotBlank String name,
        String description,
        @NotNull TrackingType trackingType,
        String defaultPassword,
        Long defaultLocationId,
        java.math.BigDecimal weightKg,
        // Nullable so existing callers that don't send it (e.g. the create form)
        // don't accidentally flip a product inactive - only applied when present.
        Boolean active
) {}
