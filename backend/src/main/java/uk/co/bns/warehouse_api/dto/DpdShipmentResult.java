package uk.co.bns.warehouse_api.dto;

import java.util.List;

public record DpdShipmentResult(
        String shipmentId,
        String consignmentNumber,
        List<String> parcelNumbers
) {}
