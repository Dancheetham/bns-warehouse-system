package uk.co.bns.warehouse_api.dto;

import uk.co.bns.warehouse_api.entity.Order;

public record DespatchConfirmationResult(
        Order order,
        AcknowledgementResult despatchEmail,
        String shopifyFulfillmentStatus,
        // Null when the order's courier isn't DPD/APC, or that courier isn't
        // configured at all (nothing to report). Otherwise a short,
        // human-readable outcome - either confirming the booking or
        // explaining why it didn't happen, since a failure here must never be
        // silent (the parcel still needs a label to actually leave the building).
        // Kept named dpdStatus (rather than renamed to courierStatus) so the
        // existing frontend field/JSON contract doesn't change - see
        // DespatchService.confirmDespatch, which now also folds APC's own
        // status into this same field.
        String dpdStatus
) {}
