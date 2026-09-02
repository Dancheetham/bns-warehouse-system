package uk.co.bns.warehouse_api.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ShopifyOrderSyncResult(
        boolean configured,
        LocalDateTime syncedAt,
        int imported,
        int alreadyImported,
        // Orders held back entirely because at least one line couldn't be matched
        // to a product by SKU - never imported partially, so nothing ships
        // incomplete without anyone noticing. Retried automatically next sync.
        List<String> skipped,
        List<String> errors
) {}
