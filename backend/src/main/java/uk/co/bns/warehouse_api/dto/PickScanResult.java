package uk.co.bns.warehouse_api.dto;

import java.util.List;

public record PickScanResult(
        PickOrderView view,
        List<Long> allocatedStockItemIds
) {}
