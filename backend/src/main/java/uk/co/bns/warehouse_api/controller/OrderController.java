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
import uk.co.bns.warehouse_api.dto.ApcLabelResult;
import uk.co.bns.warehouse_api.dto.ApcOrderResult;
import uk.co.bns.warehouse_api.dto.ApcServiceLookupResult;
import uk.co.bns.warehouse_api.dto.DpdLabelResult;
import uk.co.bns.warehouse_api.dto.DpdShipmentResult;
import uk.co.bns.warehouse_api.dto.OrderCreditStatus;
import uk.co.bns.warehouse_api.dto.OrderRequest;
import uk.co.bns.warehouse_api.dto.PaymentRequest;
import uk.co.bns.warehouse_api.dto.PaymentView;
import uk.co.bns.warehouse_api.dto.ReleaseForDespatchRequest;
import uk.co.bns.warehouse_api.dto.GdmsRunResult;
import uk.co.bns.warehouse_api.entity.Order;
import uk.co.bns.warehouse_api.service.AcknowledgementService;
import uk.co.bns.warehouse_api.service.ApcShippingService;
import uk.co.bns.warehouse_api.service.DpdShippingService;
import uk.co.bns.warehouse_api.service.GdmsEndOfDayService;
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
    private final ApcShippingService apcShippingService;
    private final GdmsEndOfDayService gdmsEndOfDayService;

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
                request.courierType(), request.collectionCourierName(), request.dpdNetworkKey(),
                request.apcServiceCode(), request.overrideCreditHold(), request.overrideReason());
    }

    // Populates the "Service" dropdown on the order screen with whatever DPD
    // actually has available right now for this order's delivery address and
    // weight - never a static list, since DPD's own docs say these can change
    // route to route and shouldn't be hardcoded.
    @GetMapping("/{id}/dpd-services")
    public uk.co.bns.warehouse_api.dto.DpdServiceLookupResult dpdServices(@PathVariable Long id) {
        return dpdShippingService.listAvailableServices(orderService.findById(id));
    }

    // Per-order manual GDMS trigger, shown in Sales Activity only for
    // GDMS-enabled customers - assigns whatever's been despatched on this
    // order (and not already synced) to the company's GDMS channel right
    // now, rather than waiting for the 16:30 scheduled run.
    @PostMapping("/{id}/gdms-assign")
    public GdmsRunResult gdmsAssign(@PathVariable Long id) {
        return gdmsEndOfDayService.runForOrder(id);
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

    // Raw ZPL - the warehouse's actual thermal printer format - same as the
    // despatch labels endpoint, so this manual "View/Print Label" button
    // prints at the label's real physical size too, not scaled to a page.
    @GetMapping("/{id}/dpd-labels")
    public ResponseEntity<byte[]> getDpdLabels(@PathVariable Long id) {
        DpdLabelResult result = dpdShippingService.getLabelsForPrint(orderService.findById(id));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"dpd-label-" + id + ".zpl\"")
                .contentType(MediaType.valueOf("application/x-zpl"))
                .body(result.rawLabelData().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    // APC equivalent of the dpd-services endpoint above - a live,
    // weight/size-aware lookup against this order's actual delivery address
    // and weight (see ApcShippingService.checkServiceAvailability), so a
    // sub-1kg order correctly offers MailPack/CourierPack/Parcel rather than
    // always showing every product regardless of what actually fits.
    @GetMapping("/{id}/apc-services")
    public ApcServiceLookupResult apcServices(@PathVariable Long id) {
        return apcShippingService.checkServiceAvailability(orderService.findById(id));
    }

    @PostMapping("/{id}/apc-shipment")
    public ApcOrderResult createApcShipment(@PathVariable Long id) {
        return apcShippingService.createOrder(orderService.findById(id));
    }

    // Raw ZPL, same treatment as the DPD label endpoint above, so "Print
    // Label" goes through the same printRaw()/print-agent flow.
    @GetMapping("/{id}/apc-labels")
    public ResponseEntity<byte[]> getApcLabels(@PathVariable Long id) {
        ApcLabelResult result = apcShippingService.getLabel(orderService.findById(id));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"apc-label-" + id + ".zpl\"")
                .contentType(MediaType.valueOf("application/x-zpl"))
                .body(result.labelData());
    }
}
