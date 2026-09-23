package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.NotBlank;

public record PublicTicketEntryRequest(
        @NotBlank String note,
        String author
) {}
