package uk.co.bns.warehouse_api.dto;

public record ContactView(
        Long id,
        Long companyId,
        String companyName,
        String name,
        String email,
        String phone,
        String position,
        boolean mainContact,
        boolean active
) {}
