package uk.co.bns.warehouse_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.dto.*;
import uk.co.bns.warehouse_api.service.PackingService;

@RestController
@RequestMapping("/api/despatch/{orderId}/packing")
@RequiredArgsConstructor
public class PackingController {

    private final PackingService packingService;

    @GetMapping
    public PackingView get(@PathVariable Long orderId) {
        return packingService.getPackingView(orderId);
    }

    @PostMapping("/cartons")
    public PackingView addCarton(@PathVariable Long orderId) {
        return packingService.addCarton(orderId);
    }

    @DeleteMapping("/cartons/{cartonId}")
    public PackingView deleteCarton(@PathVariable Long orderId, @PathVariable Long cartonId) {
        return packingService.deleteCarton(orderId, cartonId);
    }

    @PutMapping("/cartons/{cartonId}/weight")
    public PackingView setWeight(@PathVariable Long orderId, @PathVariable Long cartonId, @RequestBody CartonWeightRequest request) {
        return packingService.setCartonWeight(orderId, cartonId, request.weightKg());
    }

    @PostMapping("/split")
    public PackingView split(@PathVariable Long orderId, @Valid @RequestBody SplitLineRequest request) {
        return packingService.splitLine(orderId, request);
    }

    @PostMapping("/split-by-quantity")
    public PackingView splitByQuantity(@PathVariable Long orderId, @Valid @RequestBody SplitLineByQuantityRequest request) {
        return packingService.splitLineByQuantity(orderId, request);
    }

    @PostMapping("/assign")
    public PackingView assign(@PathVariable Long orderId, @Valid @RequestBody AssignCartonLineRequest request) {
        return packingService.assignLine(orderId, request);
    }
}
