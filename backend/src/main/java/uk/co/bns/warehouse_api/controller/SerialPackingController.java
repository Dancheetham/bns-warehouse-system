package uk.co.bns.warehouse_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.dto.AssignCartonItemRequest;
import uk.co.bns.warehouse_api.dto.CartonWeightRequest;
import uk.co.bns.warehouse_api.dto.SerialPackingView;
import uk.co.bns.warehouse_api.service.SerialPackingService;

@RestController
@RequestMapping("/api/despatch/{orderId}/serial-packing")
@RequiredArgsConstructor
public class SerialPackingController {

    private final SerialPackingService serialPackingService;

    @GetMapping
    public SerialPackingView get(@PathVariable Long orderId) {
        return serialPackingService.getPackingView(orderId);
    }

    @PostMapping("/cartons")
    public SerialPackingView addCarton(@PathVariable Long orderId) {
        return serialPackingService.addCarton(orderId);
    }

    @DeleteMapping("/cartons/{cartonId}")
    public SerialPackingView deleteCarton(@PathVariable Long orderId, @PathVariable Long cartonId) {
        return serialPackingService.deleteCarton(orderId, cartonId);
    }

    @PutMapping("/cartons/{cartonId}/weight")
    public SerialPackingView setWeight(@PathVariable Long orderId, @PathVariable Long cartonId, @RequestBody CartonWeightRequest request) {
        return serialPackingService.setCartonWeight(orderId, cartonId, request.weightKg());
    }

    @PostMapping("/assign")
    public SerialPackingView assign(@PathVariable Long orderId, @Valid @RequestBody AssignCartonItemRequest request) {
        return serialPackingService.assignItem(orderId, request);
    }
}
