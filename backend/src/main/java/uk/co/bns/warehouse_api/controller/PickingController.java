package uk.co.bns.warehouse_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.dto.*;
import uk.co.bns.warehouse_api.service.PickingService;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PickingController {

    private final PickingService pickingService;

    @GetMapping("/picking/ready")
    public List<OrderPickSummary> readyToPick() {
        return pickingService.readyToPick();
    }

    @GetMapping("/orders/{id}/picking")
    public PickOrderView getPickView(@PathVariable Long id) {
        return pickingService.getPickView(id);
    }

    @PostMapping("/orders/{id}/picking/start")
    public PickOrderView start(@PathVariable Long id, @RequestBody(required = false) PickStartRequest request) {
        return pickingService.start(id, request);
    }

    @PostMapping("/orders/{id}/picking/scan")
    public PickScanResult scan(@PathVariable Long id, @Valid @RequestBody PickScanRequest request) {
        return pickingService.scan(id, request);
    }

    @PostMapping("/orders/{id}/picking/quantity")
    public PickScanResult pickQuantity(@PathVariable Long id, @Valid @RequestBody PickQuantityRequest request) {
        return pickingService.pickQuantity(id, request);
    }

    @PostMapping("/orders/{id}/picking/undo")
    public PickOrderView undo(@PathVariable Long id, @Valid @RequestBody PickUndoRequest request) {
        return pickingService.undo(id, request);
    }

    @PostMapping("/orders/{id}/picking/complete")
    public PickOrderView complete(@PathVariable Long id, @RequestBody(required = false) PickStartRequest request) {
        return pickingService.complete(id, request != null ? request.pickedBy() : null);
    }
}
