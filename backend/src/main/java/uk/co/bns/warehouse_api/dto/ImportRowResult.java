package uk.co.bns.warehouse_api.dto;

public record ImportRowResult(
        String sku,
        int poQuantity,
        int spreadsheetQuantity,
        boolean matches
) {}
