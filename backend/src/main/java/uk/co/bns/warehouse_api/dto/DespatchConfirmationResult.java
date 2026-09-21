package uk.co.bns.warehouse_api.dto;

import uk.co.bns.warehouse_api.entity.Order;

public record DespatchConfirmationResult(
        Order order,
        AcknowledgementResult despatchEmail,
        String shopifyFulfillmentStatus,
        // Null when DPD isn't configured at all (nothing to report). Otherwise a
        // short, human-readable outcome - either confirming the booking or
        // explaining why it didn't happen, since a failure here must never be
        // silent (the parcel still needs a label to actually leave the building).
        String dpdStatus
) {}
