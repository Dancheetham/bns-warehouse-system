package uk.co.bns.warehouse_api.dto;

import java.util.List;

public record ImportResult(
        boolean success,
        int cartonsCreated,
        int itemsCreated,
        List<ImportRowResult> lineValidation,
        List<String> errors
) {}
