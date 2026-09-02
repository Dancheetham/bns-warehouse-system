package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RmaItemSubmission(
        @NotNull Long productId,
        String identifier,
        @Positive int quantity,
        boolean faulty,
        String grandstreamTicketNumber,
        String reasonForReturn
) {}
