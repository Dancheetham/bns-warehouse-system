package uk.co.bns.warehouse_api.dto;

import java.time.LocalDateTime;

public record ShopifyStatus(
        // App-level config (client id/secret/shop domain) is present - required
        // before "Connect to Shopify" can even be attempted.
        boolean appConfigured,
        // A real access token has actually been obtained via the OAuth handshake -
        // required before a sync can run. Distinct from appConfigured: an app can
        // be fully configured and still not connected yet (or disconnected later).
        boolean connected,
        String shopDomain,
        LocalDateTime lastSyncedAt,
        long needsReviewCount,
        long totalSyncedProducts,
        LocalDateTime lastCompanySyncedAt,
        LocalDateTime lastOrderSyncedAt,
        LocalDateTime lastStockPushedAt
) {}

