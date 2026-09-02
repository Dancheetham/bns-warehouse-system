package uk.co.bns.warehouse_api.dto;

import uk.co.bns.warehouse_api.enums.RmaStatus;

import java.time.LocalDateTime;
import java.util.List;

public record RmaDetailView(
        Long id,
        String publicReference,
        String rmaNumber,
        RmaStatus status,
        String customerName,
        String customerCompany,
        String customerAddress,
        String contactName,
        String contactPhone,
        String contactEmail,
        String deliveryName,
        String deliveryTown,
        String deliveryCountry,
        String deliveryPostcode,
        String deliveryCountryCode,
        Long originalOrderId,
        String originalOrderNumber,
        Long replacementOrderId,
        String replacementOrderNumber,
        Long creditOrderId,
        String creditOrderNumber,
        String notes,
        LocalDateTime submittedAt,
        LocalDateTime approvedAt,
        String approvedBy,
        LocalDateTime rejectedAt,
        String rejectedBy,
        String rejectionReason,
        LocalDateTime receivedAt,
        String receivedBy,
        List<RmaItemView> items
) {}
