package uk.co.bns.warehouse_api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.dto.ShopifyCompanySyncResult;
import uk.co.bns.warehouse_api.dto.ShopifyOrderSyncResult;
import uk.co.bns.warehouse_api.dto.ShopifyStatus;
import uk.co.bns.warehouse_api.dto.ShopifySyncResult;
import uk.co.bns.warehouse_api.service.ShopifyCompanySyncService;
import uk.co.bns.warehouse_api.service.ShopifyOrderSyncService;
import uk.co.bns.warehouse_api.service.ShopifyProductSyncService;

@RestController
@RequestMapping("/api/shopify")
@RequiredArgsConstructor
public class ShopifySyncController {

    private final ShopifyProductSyncService shopifyProductSyncService;
    private final ShopifyCompanySyncService shopifyCompanySyncService;
    private final ShopifyOrderSyncService shopifyOrderSyncService;

    @GetMapping("/status")
    public ShopifyStatus status() {
        return shopifyProductSyncService.getStatus(
                shopifyCompanySyncService.getLastSyncedAt(), shopifyOrderSyncService.getLastSyncedAt());
    }

    @PostMapping("/sync")
    public ShopifySyncResult sync() {
        return shopifyProductSyncService.sync();
    }

    @PostMapping("/sync-companies")
    public ShopifyCompanySyncResult syncCompanies() {
        return shopifyCompanySyncService.sync();
    }

    @PostMapping("/sync-orders")
    public ShopifyOrderSyncResult syncOrders() {
        return shopifyOrderSyncService.sync();
    }
}
