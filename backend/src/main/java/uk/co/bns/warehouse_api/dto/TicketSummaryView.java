package uk.co.bns.warehouse_api.dto;

import uk.co.bns.warehouse_api.enums.TicketStatus;

import java.time.LocalDateTime;

/** Lightweight view used for ticket lists (all tickets, a company's tickets, an
 *  order's linked ticket) - leaves out the timeline so listing doesn't have to
 *  pull every entry for every ticket. See TicketView for the full single-ticket
 *  shape. */
public record TicketSummaryView(
        Long id,
        String ticketNumber,
        String title,
        String callerName,
        String phone,
        String email,
        Long companyId,
        String companyName,
        Long orderId,
        String orderNumber,
        TicketStatus status,
        Integer talkTimeMinutes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
