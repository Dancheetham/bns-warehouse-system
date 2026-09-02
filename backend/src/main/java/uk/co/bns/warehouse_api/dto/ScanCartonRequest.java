package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.NotBlank;

public record ScanCartonRequest(
        @NotBlank String batchCode,
        String scannedBy
) {}
