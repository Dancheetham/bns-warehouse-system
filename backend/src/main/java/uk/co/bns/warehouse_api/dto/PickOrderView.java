package uk.co.bns.warehouse_api.dto;

import uk.co.bns.warehouse_api.enums.PickingStatus;

import java.util.List;

public record PickOrderView(
        Long orderId,
        String orderNumber,
        String customerName,
        PickingStatus pickingStatus,
        String pickedBy,
        List<PickLineView> lines
) {}
