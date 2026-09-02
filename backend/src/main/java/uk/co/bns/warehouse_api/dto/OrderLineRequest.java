package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record OrderLineRequest(
        @NotNull Long productId,
        @PositiveOrZero Integer quantityOrdered,
        @PositiveOrZero Integer quantityDespatched,
        BigDecimal unitPrice,
        String notes
) {}
