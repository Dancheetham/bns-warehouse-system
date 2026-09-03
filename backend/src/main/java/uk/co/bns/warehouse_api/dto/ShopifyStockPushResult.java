package uk.co.bns.warehouse_api.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ShopifyStockPushResult(
        boolean configured,
        LocalDateTime pushedAt,
        int pushed,
        List<String> skipped,
        List<String> errors
) {}
