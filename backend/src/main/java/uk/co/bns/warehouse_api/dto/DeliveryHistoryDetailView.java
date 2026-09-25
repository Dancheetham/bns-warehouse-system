package uk.co.bns.warehouse_api.dto;

import java.util.List;

/** Delivery History's click-through detail page - the header row's fields plus every packed item. */
public record DeliveryHistoryDetailView(
        DeliveryHistoryView order,
        List<DeliveryHistoryItemView> items
) {}
