package uk.co.bns.warehouse_api.dto;

import uk.co.bns.warehouse_api.entity.Order;

public record DespatchConfirmationResult(
        Order order,
        AcknowledgementResult despatchEmail,
        String shopifyFulfillmentStatus
) {}
