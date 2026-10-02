package uk.co.bns.warehouse_api.dto;

import java.util.List;

/**
 * Delivery History's click-through detail page - the header row's fields
 * (showing the order's most recent delivery), every packed item across the
 * whole order, the per-carton SKU summary (see
 * DeliveryHistoryCartonSummaryRow), and every individual delivery this
 * order has ever had (see DeliveryView) - one if it only ever shipped in
 * one go, more if it was despatched in separate consignments.
 */
public record DeliveryHistoryDetailView(
        DeliveryHistoryView order,
        List<DeliveryHistoryItemView> items,
        List<DeliveryHistoryCartonSummaryRow> cartonSummary,
        List<DeliveryView> deliveries
) {}
