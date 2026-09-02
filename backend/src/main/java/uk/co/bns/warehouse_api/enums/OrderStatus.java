package uk.co.bns.warehouse_api.enums;

public enum OrderStatus {
    ON_HOLD,
    AWAITING_DESPATCH,
    CANCELLED,
    COMPLETED,
    PARTIALLY_DESPATCHED,
    // Quote order types only - a quote sitting unconverted to a real order
    AWAITING_CONVERSION
}
