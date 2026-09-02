package uk.co.bns.warehouse_api.dto;

public record ApproveRmaRequest(
        String approvedBy,
        String deliveryName,
        String deliveryTown,
        String deliveryCountry,
        String deliveryPostcode,
        String deliveryCountryCode
) {}
