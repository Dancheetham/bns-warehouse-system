package uk.co.bns.warehouse_api.dto;

import uk.co.bns.warehouse_api.enums.PickingStatus;

import java.time.LocalDateTime;

public record OrderPickSummary(
        Long orderId,
        String orderNumber,
        String customerName,
        LocalDateTime orderDate,
        int lineCount,
        PickingStatus pickingStatus,
        String pickedBy
) {}
