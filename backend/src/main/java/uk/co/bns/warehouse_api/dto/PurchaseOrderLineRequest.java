package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PurchaseOrderLineRequest(
        @NotNull Long productId,
        @Positive Integer quantityOrdered,
        BigDecimal unitCost,
        String notes
) {}
