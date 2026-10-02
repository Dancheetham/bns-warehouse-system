package uk.co.bns.warehouse_api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row of the "Deliveries" table on an order's Delivery History detail
 * page - one real despatch/consignment, with its own trackable Delivery
 * Number. See entity/Delivery.java.
 */
public record DeliveryView(
        String deliveryNumber,
        LocalDateTime despatchedAt,
        boolean partial,
        String courier,
        String courierMethod,
        String consignmentNumber,
        BigDecimal shippingCost
) {}
