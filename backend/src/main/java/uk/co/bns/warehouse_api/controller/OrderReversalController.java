package uk.co.bns.warehouse_api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.entity.Order;
import uk.co.bns.warehouse_api.service.OrderReversalService;

@RestController
@RequestMapping("/api/orders/{orderId}/reversal")
@RequiredArgsConstructor
public class OrderReversalController {

    private final OrderReversalService orderReversalService;

    @PostMapping("/to-despatch")
    public Order reverseToDespatch(@PathVariable Long orderId) {
        return orderReversalService.reverseToDespatch(orderId);
    }

    @PostMapping("/cancel")
    public Order cancelAndReturnToStock(@PathVariable Long orderId) {
        return orderReversalService.cancelAndReturnToStock(orderId);
    }
}
