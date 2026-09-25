package uk.co.bns.warehouse_api.dto;

/**
 * shopifyPushResult is a short human-readable outcome (same pattern as
 * ShopifyFulfillmentService.pushFulfillment) - null when the invoice has no
 * Shopify-linked order to push to at all, so the frontend only shows it when
 * there's something worth showing.
 */
public record RecordPaymentResult(
        InvoicePaymentView payment,
        OutstandingInvoiceView invoice,
        String shopifyPushResult
) {}
