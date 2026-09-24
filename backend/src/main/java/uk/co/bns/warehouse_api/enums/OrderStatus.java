package uk.co.bns.warehouse_api.enums;

public enum OrderStatus {
    ON_HOLD,
    AWAITING_DESPATCH,
    CANCELLED,
    // Reached once every line has actually been invoiced (or, for a
    // CREDIT_REFUND order, credited) - see InvoiceService. An order with no
    // Company (no credit account - already paid at checkout, e.g. Shopify)
    // skips this and goes straight from despatch to COMPLETED instead, since
    // there's nothing left to invoice after the fact.
    COMPLETED,
    PARTIALLY_DESPATCHED,
    // Quote order types only - a quote sitting unconverted to a real order
    AWAITING_CONVERSION,
    // Fully despatched (or, for a CREDIT_REFUND order, ready to credit) and
    // belongs to a Company (credit account) - sits here until Generate
    // Invoices (InvoiceService) has invoiced every line, at which point it
    // moves to COMPLETED. See DespatchService/RmaService for where this is set.
    INVOICE_PENDING
}
