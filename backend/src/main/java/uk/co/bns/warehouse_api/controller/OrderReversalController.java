package uk.co.bns.warehouse_api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.dto.OrderReversalResult;
import uk.co.bns.warehouse_api.service.OrderReversalService;

@RestController
@RequestMapping("/api/orders/{orderId}/reversal")
@RequiredArgsConstructor
public class OrderReversalController {

    private final OrderReversalService orderReversalService;

    @PostMapping("/to-despatch")
    public OrderReversalResult reverseToDespatch(@PathVariable Long orderId) {
        return orderReversalService.reverseToDespatch(orderId);
    }

    @PostMapping("/cancel")
    public OrderReversalResult cancelAndReturnToStock(@PathVariable Long orderId) {
        return orderReversalService.cancelAndReturnToStock(orderId);
    }
}
