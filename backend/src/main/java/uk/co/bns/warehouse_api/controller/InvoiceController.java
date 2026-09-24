package uk.co.bns.warehouse_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.dto.GenerateInvoicesRequest;
import uk.co.bns.warehouse_api.dto.GenerateInvoicesResult;
import uk.co.bns.warehouse_api.dto.PendingInvoiceLineView;
import uk.co.bns.warehouse_api.entity.Invoice;
import uk.co.bns.warehouse_api.enums.InvoiceType;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.repository.InvoiceRepository;
import uk.co.bns.warehouse_api.service.InvoicePdfService;
import uk.co.bns.warehouse_api.service.InvoiceService;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoicePdfService invoicePdfService;
    private final InvoiceRepository invoiceRepository;

    @GetMapping("/pending")
    public List<PendingInvoiceLineView> pending(@RequestParam InvoiceType type) {
        return invoiceService.pending(type);
    }

    @PostMapping("/generate")
    public GenerateInvoicesResult generate(@Valid @RequestBody GenerateInvoicesRequest request, Authentication authentication) {
        return invoiceService.generate(request, authentication.getName());
    }

    /**
     * Re-renders the PDF on demand rather than serving the file saved to
     * disk - so a re-download still works even if the local copy went
     * missing (a fresh volume, a moved invoices directory) and always
     * reflects exactly what's in the database either way.
     */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Invoice " + id + " not found"));
        byte[] pdf = invoicePdfService.generate(invoice);
        String filename = (invoice.getInvoiceType().name().equals("CREDIT_NOTE") ? "Credit-Note-" : "Invoice-")
                + invoice.getInvoiceNumber() + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + new String(filename.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1) + "\"")
                .body(pdf);
    }
}
