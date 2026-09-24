package uk.co.bns.warehouse_api.enums;

/**
 * Mirrors OrderType.ORDER / OrderType.CREDIT_REFUND, but named for what it
 * produces rather than the order it comes from - the "Invoice" / "Credit"
 * radio buttons on the Generate Invoices page pick one of these, which in
 * turn decides which order type's INVOICE_PENDING lines are shown.
 */
public enum InvoiceType {
    INVOICE,
    CREDIT_NOTE
}
