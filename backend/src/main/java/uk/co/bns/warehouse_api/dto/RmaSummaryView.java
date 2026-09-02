package uk.co.bns.warehouse_api.dto;

import uk.co.bns.warehouse_api.enums.RmaStatus;

import java.time.LocalDateTime;

public record RmaSummaryView(
        Long id,
        String publicReference,
        String rmaNumber,
        RmaStatus status,
        String customerName,
        LocalDateTime submittedAt,
        int itemCount,
        boolean anyUnmatched,
        boolean anyFaulty
) {}
