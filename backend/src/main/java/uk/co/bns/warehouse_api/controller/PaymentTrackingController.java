package uk.co.bns.warehouse_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.dto.ApplyCreditBalanceRequest;
import uk.co.bns.warehouse_api.dto.OutstandingInvoiceView;
import uk.co.bns.warehouse_api.dto.RecordInvoicePaymentRequest;
import uk.co.bns.warehouse_api.dto.RecordPaymentResult;
import uk.co.bns.warehouse_api.service.PaymentTrackingService;

import java.util.List;

@RestController
@RequestMapping("/api/payment-tracking")
@RequiredArgsConstructor
public class PaymentTrackingController {

    private final PaymentTrackingService paymentTrackingService;

    @GetMapping("/outstanding")
    public List<OutstandingInvoiceView> outstanding() {
        return paymentTrackingService.outstanding();
    }

    @PostMapping("/record-payment")
    public RecordPaymentResult recordPayment(@Valid @RequestBody RecordInvoicePaymentRequest request, Authentication authentication) {
        return paymentTrackingService.recordPayment(request, authentication.getName());
    }

    @PostMapping("/apply-credit-balance")
    public RecordPaymentResult applyCreditBalance(@Valid @RequestBody ApplyCreditBalanceRequest request, Authentication authentication) {
        return paymentTrackingService.applyCreditBalance(request, authentication.getName());
    }
}
