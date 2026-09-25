package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.bns.warehouse_api.dto.ApplyCreditBalanceRequest;
import uk.co.bns.warehouse_api.dto.InvoicePaymentView;
import uk.co.bns.warehouse_api.dto.OutstandingInvoiceView;
import uk.co.bns.warehouse_api.dto.RecordInvoicePaymentRequest;
import uk.co.bns.warehouse_api.dto.RecordPaymentResult;
import uk.co.bns.warehouse_api.entity.Company;
import uk.co.bns.warehouse_api.entity.Invoice;
import uk.co.bns.warehouse_api.entity.Payment;
import uk.co.bns.warehouse_api.enums.InvoiceType;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.exception.ValidationException;
import uk.co.bns.warehouse_api.repository.InvoiceRepository;
import uk.co.bns.warehouse_api.repository.PaymentRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * "Payment Tracking" - the list of outstanding invoices, recording money
 * coming in against one, and applying a company's stored credit balance
 * (see Company.creditBalance / InvoiceService's RMA auto-apply) to one by
 * hand. Deliberately one payment = one invoice for now (Dan's call for v1 -
 * splitting a single bank transfer across several invoices in one action is
 * a manual multi-step process here, same as it is today).
 */
@Service
@RequiredArgsConstructor
public class PaymentTrackingService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final ShopifyPaymentPushService shopifyPaymentPushService;
    private final SettingsService settingsService;

    /** Every unpaid INVOICE (never a credit note) across every company - Payment Tracking filters/searches client-side from this. */
    public List<OutstandingInvoiceView> outstanding() {
        return invoiceRepository.findAll().stream()
                .filter(i -> i.getInvoiceType() == InvoiceType.INVOICE)
                .filter(i -> i.getGrandTotal().subtract(i.getPaidAmount()).compareTo(BigDecimal.ZERO) > 0)
                .map(this::toOutstandingView)
                .sorted((a, b) -> b.daysOverTerms() - a.daysOverTerms())
                .toList();
    }

    @Transactional
    public RecordPaymentResult recordPayment(RecordInvoicePaymentRequest request, String performedByName) {
        Invoice invoice = invoiceRepository.findById(request.invoiceId())
                .orElseThrow(() -> new NotFoundException("Invoice " + request.invoiceId() + " not found"));
        applyAmount(invoice, request.amount(), request.reference(), request.notes(), performedByName, false);
        invoiceRepository.save(invoice);

        String shopifyResult = shopifyPaymentPushService.pushIfFullyPaid(invoice);
        return new RecordPaymentResult(latestPaymentView(invoice), toOutstandingView(invoice), shopifyResult);
    }

    @Transactional
    public RecordPaymentResult applyCreditBalance(ApplyCreditBalanceRequest request, String performedByName) {
        Invoice invoice = invoiceRepository.findById(request.invoiceId())
                .orElseThrow(() -> new NotFoundException("Invoice " + request.invoiceId() + " not found"));
        Company company = invoice.getCompany();
        if (request.amount().compareTo(company.getCreditBalance()) > 0) {
            throw new ValidationException(company.getName() + " only has " + company.getCreditBalance()
                    + " of credit balance available - can't apply " + request.amount());
        }
        company.setCreditBalance(company.getCreditBalance().subtract(request.amount()));
        applyAmount(invoice, request.amount(), "Credit balance applied", null, performedByName, true);
        invoiceRepository.save(invoice);

        String shopifyResult = shopifyPaymentPushService.pushIfFullyPaid(invoice);
        return new RecordPaymentResult(latestPaymentView(invoice), toOutstandingView(invoice), shopifyResult);
    }

    /**
     * Applies an amount to an invoice, capping what's recorded against it at
     * what's actually still owing - anything beyond that spills into the
     * company's credit balance instead of overpaying the invoice on paper
     * (Dan: "on the crazy chance that they overpay... allow the credit
     * allowance to go over the credit limit until the extra balance is used").
     */
    private void applyAmount(Invoice invoice, BigDecimal amount, String reference, String notes,
                              String performedByName, boolean fromCreditBalance) {
        BigDecimal stillOwing = invoice.getGrandTotal().subtract(invoice.getPaidAmount());
        BigDecimal appliedToInvoice = amount.min(stillOwing.max(BigDecimal.ZERO));
        BigDecimal excess = amount.subtract(appliedToInvoice);

        if (appliedToInvoice.compareTo(BigDecimal.ZERO) > 0) {
            Payment payment = new Payment();
            payment.setInvoice(invoice);
            payment.setOrder(invoice.getLines().isEmpty() ? null : invoice.getLines().get(0).getOrder());
            payment.setAmount(appliedToInvoice);
            payment.setReceivedAt(LocalDateTime.now());
            payment.setReference(reference);
            payment.setNotes(notes);
            payment.setRecordedBy(performedByName);
            payment.setFromCreditBalance(fromCreditBalance);
            paymentRepository.save(payment);
            invoice.setPaidAmount(invoice.getPaidAmount().add(appliedToInvoice));
        }

        if (excess.compareTo(BigDecimal.ZERO) > 0) {
            Company company = invoice.getCompany();
            company.setCreditBalance(company.getCreditBalance().add(excess));
        }
    }

    private InvoicePaymentView latestPaymentView(Invoice invoice) {
        return paymentRepository.findByInvoice_IdOrderByReceivedAtDesc(invoice.getId()).stream()
                .findFirst()
                .map(p -> new InvoicePaymentView(p.getId(), p.getAmount(), p.getReceivedAt(), p.getReference(),
                        p.getNotes(), p.getRecordedBy(), p.isFromCreditBalance()))
                .orElse(null);
    }

    public OutstandingInvoiceView toOutstandingView(Invoice invoice) {
        Company company = invoice.getCompany();
        int termsDays = company.getPaymentTermsDays() != null
                ? company.getPaymentTermsDays()
                : Integer.parseInt(settingsService.get("payment_terms_days", "30"));
        int daysSince = (int) ChronoUnit.DAYS.between(invoice.getGenerationDate(), LocalDate.now());
        int daysOverTerms = Math.max(0, daysSince - termsDays);
        List<String> orderNumbers = invoice.getLines().stream()
                .map(l -> l.getOrder().getOrderNumber())
                .distinct()
                .toList();
        BigDecimal outstanding = invoice.getGrandTotal().subtract(invoice.getPaidAmount());
        return new OutstandingInvoiceView(invoice.getId(), invoice.getInvoiceNumber(), company.getId(), company.getName(),
                invoice.getGenerationDate(), invoice.getGrandTotal(), invoice.getPaidAmount(), outstanding,
                daysSince, termsDays, daysOverTerms, orderNumbers);
    }
}
