package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.NotBlank;
import uk.co.bns.warehouse_api.enums.TicketStatus;

public record TicketRequest(
        @NotBlank String title,
        String callerName,
        String phone,
        String email,
        Long companyId,
        Long orderId,
        TicketStatus status,
        // Total minutes on the phone so far - null on create means "start at 0",
        // not "leave unset", since the column itself is non-nullable.
        Integer talkTimeMinutes
) {}
