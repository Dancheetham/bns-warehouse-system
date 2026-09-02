package uk.co.bns.warehouse_api.dto;

import java.util.List;

public record PackingView(
        Long orderId,
        String orderNumber,
        String customerName,
        List<PackLineView> unassignedLines,
        List<CartonView> cartons,
        boolean allAssigned
) {}
