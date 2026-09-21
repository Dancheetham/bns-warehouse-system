package uk.co.bns.warehouse_api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.dto.DespatchConfirmationResult;
import uk.co.bns.warehouse_api.dto.DpdLabelResult;
import uk.co.bns.warehouse_api.dto.OrderPickSummary;
import uk.co.bns.warehouse_api.entity.Order;
import uk.co.bns.warehouse_api.exception.ValidationException;
import uk.co.bns.warehouse_api.service.DespatchService;
import uk.co.bns.warehouse_api.service.DpdShippingService;
import uk.co.bns.warehouse_api.service.OrderService;
import uk.co.bns.warehouse_api.service.SettingsService;
import uk.co.bns.warehouse_api.service.ShippingLabelService;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/despatch")
@RequiredArgsConstructor
public class DespatchController {

    private final DespatchService despatchService;
    private final ShippingLabelService shippingLabelService;
    private final DpdShippingService dpdShippingService;
    private final OrderService orderService;
    private final SettingsService settingsService;

    @GetMapping("/ready-to-pack")
    public List<OrderPickSummary> readyToPack() {
        return despatchService.readyToPack();
    }

    @PostMapping("/{orderId}/confirm")
    public DespatchConfirmationResult confirm(@PathVariable Long orderId, Authentication authentication) {
        return despatchService.confirmDespatch(orderId, authentication.getName());
    }

    // Once a real DPD shipment has been booked for this order (normally
    // automatic, at confirmDespatch above), this serves the real DPD label
    // instead of the old dummy placeholder PDF - as HTML, since that's what
    // DPD's own label endpoint returns for printerType 0 and it opens/prints
    // fine directly in a browser tab with no extra plumbing needed. Falls
    // back to the placeholder PDF for any order that was despatched without
    // a DPD shipment (DPD not configured, or booking failed).
    @GetMapping("/{orderId}/labels")
    public ResponseEntity<byte[]> labels(@PathVariable Long orderId) {
        Order order = orderService.findById(orderId);
        if (order.getDpdShipmentId() != null) {
            DpdLabelResult label = dpdShippingService.getLabels(order, 0, 203, "text/html");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"dpd-label-" + orderId + ".html\"")
                    .contentType(MediaType.TEXT_HTML)
                    .body(label.rawLabelData().getBytes(StandardCharsets.UTF_8));
        }
        if (!"true".equals(settingsService.get("print_sample_labels", "true"))) {
            throw new ValidationException(
                    "No DPD shipment was booked for this order, and placeholder sample labels are turned off in Settings > DPD");
        }
        byte[] pdf = shippingLabelService.generate(orderId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"labels-" + orderId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
