package uk.co.bns.warehouse_api.dto;

import java.math.BigDecimal;

public record ReleaseForDespatchRequest(
        BigDecimal shippingCost,
        String courierMethod,
        boolean overrideCreditHold,
        String overrideReason
) {}
