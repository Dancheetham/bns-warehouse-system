package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.Valid;

import java.util.List;

public record RmaSubmissionRequest(
        @NotBlank String customerName,
        String customerCompany,
        String customerAddress,
        String contactName,
        String contactPhone,
        String contactEmail,
        // Only actually required (enforced) if at least one item is faulty -
        // that's when a replacement might need shipping out.
        String deliveryName,
        String deliveryTown,
        String deliveryCountry,
        String deliveryPostcode,
        String deliveryCountryCode,
        @NotEmpty @Valid List<RmaItemSubmission> items
) {}
