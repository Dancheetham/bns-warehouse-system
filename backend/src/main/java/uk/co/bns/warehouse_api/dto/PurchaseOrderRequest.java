package uk.co.bns.warehouse_api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record PurchaseOrderRequest(
        @NotNull Long supplierId,
        LocalDate expectedDate,
        @NotEmpty @Valid List<PurchaseOrderLineRequest> lines
) {}
