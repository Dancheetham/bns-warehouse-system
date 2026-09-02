package uk.co.bns.warehouse_api.enums;

public enum RmaStatus {
    // Sitting in the inbox, awaiting staff review.
    SUBMITTED,
    REJECTED,
    // Checks passed, RMA number assigned, awaiting the physical return.
    APPROVED,
    // Physical return processed - checks done, stock booked back in, credited.
    RECEIVED
}
