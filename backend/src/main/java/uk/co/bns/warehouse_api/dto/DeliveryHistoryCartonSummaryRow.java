package uk.co.bns.warehouse_api.dto;

/**
 * One row of Delivery History detail's carton summary - how many of a given
 * SKU actually went into a given carton. Built from the same two sources as
 * {@link DeliveryHistoryItemView} (a Serial-Packing StockItem.carton, or a
 * Split-Packing/NONE-tracking CartonLine), but summed per carton+product
 * rather than listed per unit - unlike the per-unit view, this is never
 * ambiguous: a CartonLine's quantity is exactly how many of that product
 * physically went into that carton, whatever packing mode was used.
 */
public record DeliveryHistoryCartonSummaryRow(
        int cartonNumber,
        String sku,
        String productName,
        int quantity
) {}
