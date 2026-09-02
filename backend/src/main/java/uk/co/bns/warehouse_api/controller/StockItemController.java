package uk.co.bns.warehouse_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.dto.MoveItemsResult;
import uk.co.bns.warehouse_api.dto.MoveStockItemsRequest;
import uk.co.bns.warehouse_api.dto.StockItemSummary;
import uk.co.bns.warehouse_api.service.StockItemLookupService;

import java.util.List;

@RestController
@RequestMapping("/api/stock-items")
@RequiredArgsConstructor
public class StockItemController {

    private final StockItemLookupService stockItemLookupService;

    @GetMapping("/mac/{mac}")
    public StockItemSummary byMac(@PathVariable String mac) {
        return stockItemLookupService.findByMac(mac);
    }

    @GetMapping("/serial/{serial}")
    public StockItemSummary bySerial(@PathVariable String serial) {
        return stockItemLookupService.findBySerial(serial);
    }

    @GetMapping("/batch/{batchCode}")
    public List<StockItemSummary> byBatch(@PathVariable String batchCode) {
        return stockItemLookupService.findByBatch(batchCode);
    }

    @GetMapping("/product/{productId}")
    public List<StockItemSummary> byProduct(@PathVariable Long productId) {
        return stockItemLookupService.listByProduct(productId);
    }

    @GetMapping("/location/{locationId}")
    public List<uk.co.bns.warehouse_api.dto.BinProductGroup> byLocation(@PathVariable Long locationId) {
        return stockItemLookupService.getBinContents(locationId);
    }

    @PostMapping("/move")
    public MoveItemsResult move(@Valid @RequestBody MoveStockItemsRequest request) {
        return stockItemLookupService.moveItems(
                request.stockItemIds(), request.toLocationId(), request.movedBy(), request.notes());
    }
}
