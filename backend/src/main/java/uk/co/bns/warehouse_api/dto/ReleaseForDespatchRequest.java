package uk.co.bns.warehouse_api.dto;

import uk.co.bns.warehouse_api.enums.CourierType;

import java.math.BigDecimal;

public record ReleaseForDespatchRequest(
        BigDecimal shippingCost,
        String courierMethod,
        CourierType courierType,
        String collectionCourierName,
        String dpdNetworkKey,
        String apcServiceCode,
        boolean overrideCreditHold,
        String overrideReason
) {}
