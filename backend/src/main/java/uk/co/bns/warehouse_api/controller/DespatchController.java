package uk.co.bns.warehouse_api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.dto.DespatchConfirmationResult;
import uk.co.bns.warehouse_api.dto.OrderPickSummary;
import uk.co.bns.warehouse_api.service.DespatchService;
import uk.co.bns.warehouse_api.service.ShippingLabelService;

import java.util.List;

@RestController
@RequestMapping("/api/despatch")
@RequiredArgsConstructor
public class DespatchController {

    private final DespatchService despatchService;
    private final ShippingLabelService shippingLabelService;

    @GetMapping("/ready-to-pack")
    public List<OrderPickSummary> readyToPack() {
        return despatchService.readyToPack();
    }

    @PostMapping("/{orderId}/confirm")
    public DespatchConfirmationResult confirm(@PathVariable Long orderId) {
        return despatchService.confirmDespatch(orderId);
    }

    @GetMapping("/{orderId}/labels")
    public ResponseEntity<byte[]> labels(@PathVariable Long orderId) {
        byte[] pdf = shippingLabelService.generate(orderId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"labels-" + orderId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
