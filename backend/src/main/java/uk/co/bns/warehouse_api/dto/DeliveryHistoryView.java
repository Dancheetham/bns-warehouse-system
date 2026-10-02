package uk.co.bns.warehouse_api.dto;

import java.time.LocalDateTime;

/**
 * One row on the Delivery History page (Sales) - one real delivery
 * (Delivery.java), not one row per order. An order despatched in two
 * separate trips shows up as two rows here, each with its own
 * deliveryNumber to search/track by, sharing the same orderNumber. Only
 * ever includes deliveries that genuinely, irreversibly went out - see
 * DeliveryHistoryService.
 */
public record DeliveryHistoryView(
        String deliveryNumber,
        boolean partial,
        Long orderId,
        String orderNumber,
        LocalDateTime despatchedAt,
        String companyName,
        String deliveryName,
        String deliveryPostcode,
        String courier,
        String deliveryMethod,
        String consignmentNumber,
        String orderStatus
) {}
