package uk.co.bns.warehouse_api.enums;

/**
 * Which courier (if any) an order is going out on. Historically the only
 * courier this system ever booked was DPD, and whether an order was "on
 * DPD" was inferred purely from whether dpdShipmentId was set - there was no
 * explicit discriminator anywhere. This makes that choice explicit, and adds
 * two more paths: NONE (no courier booking at all - hides the whole
 * service/booking/label UI) and COLLECTION (an external courier BNS doesn't
 * book or label itself - see CollectionCourierOption - just records which
 * one was used).
 */
public enum CourierType {
    NONE,
    DPD,
    APC,
    COLLECTION
}
