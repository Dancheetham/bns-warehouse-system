package uk.co.bns.warehouse_api.enums;

/**
 * Per-company setting (Company.invoiceGrouping) for how a single Generate
 * Invoices run bundles that company's selected order lines into invoices -
 * see InvoiceService.generate(). Independent of line-level selection: either
 * way, only the lines actually ticked on the day get invoiced.
 */
public enum InvoiceGrouping {
    // One invoice per order - lines from different orders never share an
    // invoice, even if generated on the same day.
    PER_ORDER,
    // One invoice per company per generation run, covering every selected
    // line across all of that company's orders in this run.
    CONSOLIDATED
}
