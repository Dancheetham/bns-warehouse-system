package uk.co.bns.warehouse_api.dto;

/**
 * One packed unit (or, for a non-serialised/batch-only product, one packed
 * quantity) on a despatched order's Delivery History detail page.
 *
 * A serial/MAC-tracked product gets one row per physical unit (quantity
 * always 1) with its real mac/serial/batch identity. A NONE-tracked product
 * has no per-unit identity to report, so it gets one row per carton it was
 * actually packed into instead, with quantity set to however much of it
 * went into that carton and every identity field left null.
 *
 * cartonNumber is null only for a serialised unit packed under SPLIT mode
 * whose order line was split across more than one carton - carton
 * assignment in that mode is tracked per quantity-slice of a line
 * (CartonLine), not per specific serial, so which exact carton that one
 * unit ended up in genuinely isn't recorded. SERIAL packing mode (where
 * BNS's own MAC/serial products are normally packed) always resolves this
 * precisely, straight from StockItem.carton.
 */
public record DeliveryHistoryItemView(
        String sku,
        String productName,
        String macAddress,
        String serialNumber,
        String wifiMacAddress,
        String batchCode,
        int quantity,
        Integer cartonNumber
) {}
