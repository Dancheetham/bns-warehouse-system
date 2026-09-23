package uk.co.bns.warehouse_api.dto;

import uk.co.bns.warehouse_api.enums.TicketStatus;

import java.time.LocalDateTime;
import java.util.List;

public record TicketView(
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
        LocalDateTime updatedAt,
        List<TicketEntryView> entries
) {}
