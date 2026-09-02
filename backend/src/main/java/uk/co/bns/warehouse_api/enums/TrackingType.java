package uk.co.bns.warehouse_api.enums;

/**
 * Determines what identifiers a product's stock is tracked by.
 * NONE   - quantity only (e.g. cables, PSUs)
 * SERIAL - individually tracked by serial number only
 * MAC    - individually tracked by MAC address (serial is also required/expected)
 */
public enum TrackingType {
    NONE,
    SERIAL,
    MAC
}
