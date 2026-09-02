package uk.co.bns.warehouse_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.dto.LocationStockSummary;
import uk.co.bns.warehouse_api.dto.MoveStockRequest;
import uk.co.bns.warehouse_api.dto.ProductRequest;
import uk.co.bns.warehouse_api.entity.Product;
import uk.co.bns.warehouse_api.service.InventoryService;
import uk.co.bns.warehouse_api.service.ProductService;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final InventoryService inventoryService;

    @GetMapping
    public List<Product> getAll() {
        return productService.findAll();
    }

    @GetMapping("/{id}")
    public Product getOne(@PathVariable Long id) {
        return productService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Product create(@Valid @RequestBody ProductRequest request) {
        return productService.create(request);
    }

    @PutMapping("/{id}")
    public Product update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @GetMapping("/{id}/stock-summary")
    public List<LocationStockSummary> getStockSummary(@PathVariable Long id) {
        return inventoryService.getStockSummary(id);
    }

    @PostMapping("/{id}/move")
    public void moveStock(@PathVariable Long id, @Valid @RequestBody MoveStockRequest request) {
        inventoryService.moveStock(id, request);
    }
}
