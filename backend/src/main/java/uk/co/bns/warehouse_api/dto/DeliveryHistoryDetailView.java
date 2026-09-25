package uk.co.bns.warehouse_api.dto;

import java.util.List;

/**
 * Delivery History's click-through detail page - the header row's fields,
 * every packed item, and the per-carton SKU summary (see
 * DeliveryHistoryCartonSummaryRow).
 */
public record DeliveryHistoryDetailView(
        DeliveryHistoryView order,
        List<DeliveryHistoryItemView> items,
        List<DeliveryHistoryCartonSummaryRow> cartonSummary,
        List<ShipmentView> previousShipments
) {}
