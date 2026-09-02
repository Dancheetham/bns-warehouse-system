package uk.co.bns.warehouse_api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.service.TestDataResetService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/test-data-reset")
@RequiredArgsConstructor
public class TestDataResetController {

    private final TestDataResetService testDataResetService;

    @GetMapping
    public Map<String, Boolean> status() {
        return Map.of("enabled", testDataResetService.isEnabled());
    }

    @PostMapping
    public void reset() {
        testDataResetService.resetStockAndPurchaseOrders();
    }

    @PostMapping("/demo-products")
    public List<String> clearDemoProducts() {
        return testDataResetService.clearDemoProductCatalog();
    }
}
