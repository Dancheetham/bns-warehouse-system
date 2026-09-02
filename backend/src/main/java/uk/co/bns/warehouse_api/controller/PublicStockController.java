package uk.co.bns.warehouse_api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.co.bns.warehouse_api.dto.PublicStockItem;
import uk.co.bns.warehouse_api.service.PublicStockService;

import java.util.List;

/**
 * Customer-facing read-only stock API. All endpoints under /api/public require a
 * valid X-API-Key header (see ApiKeyInterceptor) - keys are managed from the
 * internal "API Access" screen.
 */
@RestController
@RequestMapping("/api/public/stock")
@RequiredArgsConstructor
public class PublicStockController {

    private final PublicStockService publicStockService;

    @GetMapping
    public List<PublicStockItem> getAllStock() {
        return publicStockService.getAllAvailableStock();
    }

    @GetMapping("/{sku}")
    public PublicStockItem getStockForSku(@PathVariable String sku) {
        return publicStockService.getStockForSku(sku);
    }
}
