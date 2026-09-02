package uk.co.bns.warehouse_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uk.co.bns.warehouse_api.dto.ImportResult;
import uk.co.bns.warehouse_api.dto.PurchaseOrderRequest;
import uk.co.bns.warehouse_api.entity.PurchaseOrder;
import uk.co.bns.warehouse_api.service.PurchaseOrderService;
import uk.co.bns.warehouse_api.service.SpreadsheetImportService;

import java.util.List;

@RestController
@RequestMapping("/api/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;
    private final SpreadsheetImportService spreadsheetImportService;

    @GetMapping
    public List<PurchaseOrder> getAll() {
        return purchaseOrderService.findAll();
    }

    @GetMapping("/{id}")
    public PurchaseOrder getOne(@PathVariable Long id) {
        return purchaseOrderService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseOrder create(@Valid @RequestBody PurchaseOrderRequest request) {
        return purchaseOrderService.create(request);
    }

    @PostMapping("/{id}/import")
    public ImportResult importShipment(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return spreadsheetImportService.importShipment(id, file);
    }
}
