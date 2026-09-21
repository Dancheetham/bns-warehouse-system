package uk.co.bns.warehouse_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.dto.*;
import uk.co.bns.warehouse_api.service.PickingService;

import java.util.List;

/**
 * Every action here is attributed to whoever's actually logged in
 * (authentication.getName(), which is the user's full name - that's what
 * the login itself is keyed on, see AppUserDetailsService), never whatever
 * a request body happens to carry. The old approach (a "picker name" field
 * cached in the browser's localStorage on the handheld) could silently go
 * stale the moment a different person logged into the same device without
 * that field being manually changed - exactly the kind of bug this
 * eliminates outright rather than papering over.
 */
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
    public PickOrderView start(@PathVariable Long id, Authentication authentication) {
        return pickingService.start(id, new PickStartRequest(authentication.getName()));
    }

    @PostMapping("/orders/{id}/picking/scan")
    public PickScanResult scan(@PathVariable Long id, @Valid @RequestBody PickScanRequest request, Authentication authentication) {
        return pickingService.scan(id, new PickScanRequest(request.orderLineId(), request.code(), authentication.getName()));
    }

    @PostMapping("/orders/{id}/picking/quantity")
    public PickScanResult pickQuantity(@PathVariable Long id, @Valid @RequestBody PickQuantityRequest request, Authentication authentication) {
        return pickingService.pickQuantity(id, new PickQuantityRequest(request.orderLineId(), request.quantity(), authentication.getName()));
    }

    @PostMapping("/orders/{id}/picking/undo")
    public PickOrderView undo(@PathVariable Long id, @Valid @RequestBody PickUndoRequest request, Authentication authentication) {
        return pickingService.undo(id, request, authentication.getName());
    }

    @PostMapping("/orders/{id}/picking/complete")
    public PickOrderView complete(@PathVariable Long id, Authentication authentication) {
        return pickingService.complete(id, authentication.getName());
    }
}
