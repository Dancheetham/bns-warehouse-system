package uk.co.bns.warehouse_api.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ShopifySyncResult(
        boolean configured,
        LocalDateTime syncedAt,
        int created,
        int updated,
        int skippedNoSku,
        List<String> errors
) {}
