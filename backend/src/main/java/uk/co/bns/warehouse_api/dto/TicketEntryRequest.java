package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.NotBlank;

public record TicketEntryRequest(
        @NotBlank String note,
        // Optional - the internal UI leaves this blank and the logged-in user's
        // name is used instead (see TicketController); present so a future
        // caller can attribute a note to someone else explicitly.
        String author
) {}
