package uk.co.bns.warehouse_api.dto;

public record AcknowledgementResult(
        boolean emailSent,
        String reason,
        String toAddress,
        String subject,
        String body
) {}
