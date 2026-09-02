package uk.co.bns.warehouse_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.dto.AcknowledgementResult;
import uk.co.bns.warehouse_api.dto.OrderCreditStatus;
import uk.co.bns.warehouse_api.dto.OrderRequest;
import uk.co.bns.warehouse_api.dto.PaymentRequest;
import uk.co.bns.warehouse_api.dto.PaymentView;
import uk.co.bns.warehouse_api.dto.ReleaseForDespatchRequest;
import uk.co.bns.warehouse_api.entity.Order;
import uk.co.bns.warehouse_api.service.AcknowledgementService;
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
    public AcknowledgementResult acknowledge(@PathVariable Long id) {
        return acknowledgementService.sendAcknowledgement(id);
    }
}
