package uk.co.bns.warehouse_api.dto;

import jakarta.validation.constraints.NotBlank;

public record CollectionCourierOptionRequest(
        @NotBlank String name,
        boolean active,
        int sortOrder
) {}
