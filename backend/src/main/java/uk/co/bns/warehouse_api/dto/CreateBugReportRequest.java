package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateBugReportRequest(
        @NotBlank String description,
        String errorCode,
        String context,
        String source
) {}
