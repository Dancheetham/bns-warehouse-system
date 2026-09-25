package uk.co.bns.warehouse_api.dto;

import java.time.LocalDateTime;

/**
 * One row on the Delivery History page (Sales) - every order that's
 * actually gone out at least once, whatever it's sitting at now (still
 * Invoice Pending, fully Completed, or Partially Despatched and waiting on
 * the rest).
 */
public record DeliveryHistoryView(
        Long orderId,
        String orderNumber,
        LocalDateTime despatchedAt,
        String companyName,
        String deliveryName,
        String deliveryPostcode,
        String courier,
        String deliveryMethod,
        String consignmentNumber,
        int parcelCount,
        String orderStatus
) {}
