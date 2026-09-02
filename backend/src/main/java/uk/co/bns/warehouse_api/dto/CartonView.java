package uk.co.bns.warehouse_api.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartonView(
        Long cartonId,
        int cartonNumber,
        BigDecimal weightKg,
        BigDecimal computedWeightKg,
        String trackingNumber,
        List<PackLineView> lines
) {}
