package uk.co.bns.warehouse_api.enums;

/**
 * Tracks the handheld pick separately from OrderStatus/despatch - a picker can be
 * partway through, or finish short of a full pick, well before packing/shipping
 * (a separate step, done on the web GUI) has happened.
 */
public enum PickingStatus {
    NOT_STARTED,
    IN_PROGRESS,
    // Every line reached its required quantity
    COMPLETE,
    // Picker finished the pick but one or more lines came up short on stock
    PARTIAL
}
