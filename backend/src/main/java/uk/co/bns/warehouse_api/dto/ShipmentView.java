package uk.co.bns.warehouse_api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One archived (superseded) shipment on an order - see Shipment.java. The
 * order's own current shipment (courier/consignment/etc, already shown on
 * DeliveryHistoryView) is never included here; this is only the earlier
 * one(s) an extra shipment has since replaced.
 */
public record ShipmentView(
        LocalDateTime shippedAt,
        String courier,
        String courierMethod,
        String dpdConsignmentNumber,
        String dpdParcelNumbers,
        BigDecimal shippingCost
) {}
