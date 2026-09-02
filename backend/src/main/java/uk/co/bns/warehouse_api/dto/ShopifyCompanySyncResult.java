package uk.co.bns.warehouse_api.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ShopifyCompanySyncResult(
        boolean configured,
        LocalDateTime syncedAt,
        int created,
        int updated,
        List<String> errors
) {}
