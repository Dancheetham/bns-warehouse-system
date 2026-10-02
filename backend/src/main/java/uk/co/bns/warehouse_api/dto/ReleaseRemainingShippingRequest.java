package uk.co.bns.warehouse_api.dto;

import uk.co.bns.warehouse_api.enums.CourierType;

import java.math.BigDecimal;

/**
 * "Add Additional Shipping" on a Partially Despatched order - releases
 * whatever's still outstanding back onto the picking queue, with the
 * courier/service for this next delivery set independently of whatever any
 * earlier delivery on the same order went out on. See
 * OrderService.releaseRemainingForShipping().
 */
public record ReleaseRemainingShippingRequest(
        BigDecimal shippingCost,
        String courierMethod,
        CourierType courierType,
        String collectionCourierName,
        String dpdNetworkKey,
        String apcServiceCode
) {}
