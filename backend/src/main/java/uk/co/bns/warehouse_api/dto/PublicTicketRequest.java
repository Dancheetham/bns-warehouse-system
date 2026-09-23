package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.NotBlank;

/** Body for POST /api/public/tickets - lets a third-party call-transcription/CRM
 *  service open a ticket straight from a finished call, authenticated the same
 *  way as the rest of /api/public (X-API-Key header). */
public record PublicTicketRequest(
        @NotBlank String title,
        String callerName,
        String phone,
        String email,
        Long companyId,
        // The order's own display number (e.g. "SO-10019"), not our internal id -
        // the external system only ever knows the number the customer read out.
        String orderNumber,
        Integer talkTimeMinutes,
        // Becomes the ticket's first timeline entry when present (e.g. a call
        // summary/transcript) - optional, so a bare ticket can still be created
        // without one.
        String note,
        // Defaults to "Call Transcript" if left blank (see TicketPublicController).
        String author
) {}
