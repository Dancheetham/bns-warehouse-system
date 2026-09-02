package uk.co.bns.warehouse_api.dto;

import java.util.List;

public record SerialPackingView(
        Long orderId,
        String orderNumber,
        String customerName,
        List<PackedItemView> unassignedItems,
        List<SerialCartonView> cartons,
        boolean allAssigned
) {}
