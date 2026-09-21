package uk.co.bns.warehouse_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.dto.AcknowledgementResult;
import uk.co.bns.warehouse_api.dto.DpdLabelResult;
import uk.co.bns.warehouse_api.dto.DpdShipmentResult;
import uk.co.bns.warehouse_api.dto.OrderCreditStatus;
import uk.co.bns.warehouse_api.dto.OrderRequest;
import uk.co.bns.warehouse_api.dto.PaymentRequest;
import uk.co.bns.warehouse_api.dto.PaymentView;
import uk.co.bns.warehouse_api.dto.ReleaseForDespatchRequest;
import uk.co.bns.warehouse_api.entity.Order;
import uk.co.bns.warehouse_api.service.AcknowledgementService;
import uk.co.bns.warehouse_api.service.DpdShippingService;
import uk.co.bns.warehouse_api.service.OrderService;
import uk.co.bns.warehouse_api.service.PaymentService;
import uk.co.bns.warehouse_api.service.PickingNoteService;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final PickingNoteService pickingNoteService;
    private final AcknowledgementService acknowledgementService;
    private final PaymentService paymentService;
    private final DpdShippingService dpdShippingService;

    @GetMapping
    public List<Order> getAll() {
        return orderService.findAll();
    }

    @GetMapping("/{id}")
    public Order getOne(@PathVariable Long id) {
        return orderService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Order create(@Valid @RequestBody OrderRequest request) {
        return orderService.create(request);
    }

    @PutMapping("/{id}")
    public Order update(@PathVariable Long id, @Valid @RequestBody OrderRequest request) {
        return orderService.update(id, request);
    }

    @PostMapping("/{id}/release-for-despatch")
    public Order releaseForDespatch(@PathVariable Long id, @RequestBody ReleaseForDespatchRequest request) {
        return orderService.releaseForDespatch(id, request.shippingCost(), request.courierMethod(),
                request.overrideCreditHold(), request.overrideReason());
    }

    @GetMapping("/{id}/credit-status")
    public OrderCreditStatus creditStatus(@PathVariable Long id) {
        return orderService.getCreditStatus(id);
    }

    @GetMapping("/{id}/payments")
    public List<PaymentView> payments(@PathVariable Long id) {
        return paymentService.findByOrder(id).stream().map(paymentService::toView).toList();
    }

    @PostMapping("/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentView recordPayment(@PathVariable Long id, @Valid @RequestBody PaymentRequest request) {
        return paymentService.toView(paymentService.record(id, request));
    }

    @GetMapping("/{id}/picking-note")
    public ResponseEntity<byte[]> pickingNote(@PathVariable Long id) {
        byte[] pdf = pickingNoteService.generate(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"picking-note-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping("/{id}/acknowledge")
    public AcknowledgementResult acknowledge(@PathVariable Long id, Authentication authentication) {
        return acknowledgementService.sendAcknowledgement(id, authentication.getName());
    }

    @PostMapping("/{id}/dpd-shipment")
    public DpdShipmentResult createDpdShipment(@PathVariable Long id) {
        return dpdShippingService.createShipment(orderService.findById(id));
    }

    // printerType/printerDpi/format follow DPD's own label API (see
    // dpd-api-findings.md) - format defaults to plain HTML since that's the
    // simplest thing for the browser to open and print directly with no
    // extra plumbing; a thermal-label integration can request text/vnd.zebra-zpl
    // instead once that's wired up to a specific printer.
    @GetMapping(value = "/{id}/dpd-labels", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getDpdLabels(@PathVariable Long id,
                                                @RequestParam(defaultValue = "0") int printerType,
                                                @RequestParam(defaultValue = "203") int printerDpi,
                                                @RequestParam(defaultValue = "text/html") String format) {
        DpdLabelResult result = dpdShippingService.getLabels(orderService.findById(id), printerType, printerDpi, format);
        return ResponseEntity.ok(result.rawLabelData());
    }
}
