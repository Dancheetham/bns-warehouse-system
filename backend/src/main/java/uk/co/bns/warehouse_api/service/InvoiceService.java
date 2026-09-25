package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.bns.warehouse_api.dto.GenerateInvoicesRequest;
import uk.co.bns.warehouse_api.dto.GenerateInvoicesResult;
import uk.co.bns.warehouse_api.dto.GeneratedInvoiceSummary;
import uk.co.bns.warehouse_api.dto.InvoiceHistoryView;
import uk.co.bns.warehouse_api.dto.PendingInvoiceLineView;
import uk.co.bns.warehouse_api.entity.Company;
import uk.co.bns.warehouse_api.entity.Invoice;
import uk.co.bns.warehouse_api.entity.InvoiceLine;
import uk.co.bns.warehouse_api.entity.Order;
import uk.co.bns.warehouse_api.entity.OrderLine;
import uk.co.bns.warehouse_api.entity.Payment;
import uk.co.bns.warehouse_api.entity.RmaRequest;
import uk.co.bns.warehouse_api.enums.InvoiceGrouping;
import uk.co.bns.warehouse_api.enums.InvoiceType;
import uk.co.bns.warehouse_api.enums.OrderStatus;
import uk.co.bns.warehouse_api.enums.OrderType;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.exception.ValidationException;
import uk.co.bns.warehouse_api.repository.InvoiceLineRepository;
import uk.co.bns.warehouse_api.repository.InvoiceRepository;
import uk.co.bns.warehouse_api.repository.OrderLineRepository;
import uk.co.bns.warehouse_api.repository.OrderRepository;
import uk.co.bns.warehouse_api.repository.PaymentRepository;
import uk.co.bns.warehouse_api.repository.RmaRequestRepository;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * "Generate Invoices" - turns selected order lines on INVOICE_PENDING orders
 * into an actual Invoice (or, for CREDIT_REFUND orders, a Credit Note): a
 * PDF, an email to the company's invoice contact, a local saved copy, and
 * the running quantityInvoiced/order-status bookkeeping that keeps a line
 * from being invoiced twice and moves an order to COMPLETED once every line
 * on it has actually been invoiced.
 *
 * Selection is deliberately per ORDER LINE, not per order or per company -
 * some accounts want everything on one order invoiced together, others want
 * it split by product type (e.g. phones on one invoice, routers on
 * another); ticking individual lines supports both without needing a
 * separate "invoice grouping" concept beyond Company.invoiceGrouping, which
 * only controls whether multiple orders' ticked lines for the same company
 * get merged into one invoice or kept one-per-order.
 */
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM");

    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final SettingsService settingsService;
    private final InvoicePdfService invoicePdfService;
    private final EmailService emailService;
    private final PaymentRepository paymentRepository;
    private final RmaRequestRepository rmaRequestRepository;

    @Value("${app.invoices-dir:/app/invoices}")
    private String invoicesDir;

    /** Every invoice/credit note ever generated - Invoice History, newest first. */
    public List<InvoiceHistoryView> history() {
        return invoiceRepository.findAll().stream()
                .sorted(Comparator.comparing(Invoice::getGenerationDate).reversed()
                        .thenComparing(Comparator.comparing(Invoice::getInvoiceNumber).reversed()))
                .map(invoice -> new InvoiceHistoryView(
                        invoice.getId(), invoice.getInvoiceNumber(), invoice.getInvoiceType(),
                        invoice.getGenerationDate(), invoice.getCompany().getName(),
                        invoice.getNetTotal(), invoice.getVatTotal(), invoice.getGrandTotal(),
                        invoice.getLines().stream().map(l -> l.getOrder().getOrderNumber()).distinct().toList()))
                .toList();
    }

    // Orders eligible for Generate Invoices - a fully despatched order
    // (INVOICE_PENDING) obviously, but also a PARTIALLY_DESPATCHED one:
    // what's actually shipped on it so far is just as invoiceable as a full
    // despatch, for the (smaller) quantity remainingQuantity works out per
    // line - it simply stays PARTIALLY_DESPATCHED afterwards rather than
    // completing, since there's still more owed.
    private static final List<OrderStatus> INVOICEABLE_STATUSES =
            List.of(OrderStatus.INVOICE_PENDING, OrderStatus.PARTIALLY_DESPATCHED);

    public List<PendingInvoiceLineView> pending(InvoiceType invoiceType) {
        OrderType orderType = orderTypeFor(invoiceType);
        List<Order> orders = orderRepository.findByStatusInAndOrderTypeOrderByOrderDateAsc(
                INVOICEABLE_STATUSES, orderType);

        List<PendingInvoiceLineView> rows = new ArrayList<>();
        for (Order order : orders) {
            if (order.getCompany() == null) continue; // shouldn't happen - defensive only, see DespatchService/RmaService
            for (OrderLine line : order.getLines()) {
                int pending = remainingQuantity(line, orderType);
                if (pending <= 0) continue;
                BigDecimal unitPrice = line.getUnitPrice() != null ? line.getUnitPrice() : BigDecimal.ZERO;
                rows.add(new PendingInvoiceLineView(
                        line.getId(), order.getId(), order.getOrderNumber(), order.getOrderDate(),
                        order.getCompany().getId(), order.getCompany().getName(),
                        line.getProduct().getSku(), line.getProduct().getName(),
                        pending, unitPrice, unitPrice.multiply(BigDecimal.valueOf(pending))));
            }
        }
        rows.sort(Comparator.comparing(PendingInvoiceLineView::companyName)
                .thenComparing(PendingInvoiceLineView::orderNumber));
        return rows;
    }

    @Transactional
    public GenerateInvoicesResult generate(GenerateInvoicesRequest request, String performedByName) {
        OrderType orderType = orderTypeFor(request.invoiceType());

        List<OrderLine> lines = orderLineRepository.findAllById(request.orderLineIds());
        if (lines.size() != request.orderLineIds().size()) {
            throw new NotFoundException("One or more selected lines no longer exist - refresh the page and try again");
        }
        for (OrderLine line : lines) {
            Order order = line.getOrder();
            if (!INVOICEABLE_STATUSES.contains(order.getStatus()) || order.getOrderType() != orderType) {
                throw new ValidationException("Order " + order.getOrderNumber()
                        + " is no longer awaiting " + (orderType == OrderType.CREDIT_REFUND ? "crediting" : "invoicing")
                        + " - someone else may have already generated it. Refresh the page and try again.");
            }
            if (remainingQuantity(line, orderType) <= 0) {
                throw new ValidationException("Line " + line.getProduct().getSku() + " on order " + order.getOrderNumber()
                        + " has already been fully invoiced - refresh the page and try again.");
            }
        }

        // Group into one Invoice per company (CONSOLIDATED) or one per
        // company+order (PER_ORDER) - a LinkedHashMap keeps generation in a
        // stable, predictable order (by however the selection came in).
        Map<String, List<OrderLine>> groups = new LinkedHashMap<>();
        for (OrderLine line : lines) {
            Company company = line.getOrder().getCompany();
            String key = company.getInvoiceGrouping() == InvoiceGrouping.CONSOLIDATED
                    ? "company:" + company.getId()
                    : "order:" + line.getOrder().getId();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(line);
        }

        List<GeneratedInvoiceSummary> summaries = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (List<OrderLine> groupLines : groups.values()) {
            Invoice invoice = buildInvoice(groupLines, request, orderType, performedByName);
            invoice = invoiceRepository.save(invoice);

            byte[] pdf = invoicePdfService.generate(invoice);
            String pdfPath = savePdf(invoice, pdf);
            invoice.setPdfPath(pdfPath);

            if (request.invoiceType() == InvoiceType.CREDIT_NOTE) {
                autoApplyRmaCredit(invoice, warnings);
            }

            emailInvoice(invoice, pdf, warnings);

            for (OrderLine line : groupLines) {
                int invoicedQty = remainingQuantity(line, orderType);
                line.setQuantityInvoiced(line.getQuantityInvoiced() + invoicedQty);
            }

            summaries.add(new GeneratedInvoiceSummary(invoice.getId(), invoice.getInvoiceNumber(),
                    invoice.getCompany().getName(), invoice.getGrandTotal(),
                    invoice.getEmailSentAt() != null, invoice.getEmailError()));
        }

        // An order only ever appears once across the groups above (all its
        // selected lines land in the same group either way), but a company's
        // orders can span several groups when CONSOLIDATED - so this pass
        // checks completion across every touched order regardless of which
        // group its lines ended up in.
        for (OrderLine line : lines) {
            Order order = line.getOrder();
            boolean fullyInvoiced = order.getLines().stream()
                    .allMatch(l -> remainingQuantity(l, orderType) <= 0);
            if (fullyInvoiced && order.getStatus() == OrderStatus.INVOICE_PENDING) {
                order.setStatus(OrderStatus.COMPLETED);
            }
        }

        return new GenerateInvoicesResult(summaries, warnings);
    }

    private Invoice buildInvoice(List<OrderLine> groupLines, GenerateInvoicesRequest request,
                                  OrderType orderType, String performedByName) {
        Company company = groupLines.get(0).getOrder().getCompany();
        BigDecimal vatRate = company.getVatRate() != null
                ? company.getVatRate()
                : new BigDecimal(settingsService.get("vat_rate", "20"));

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(nextInvoiceNumber());
        invoice.setInvoiceType(request.invoiceType());
        invoice.setCompany(company);
        invoice.setGenerationDate(request.generationDate());
        invoice.setCustomerName(company.getName());
        invoice.setVatRate(vatRate);
        invoice.setCreatedBy(performedByName);

        BigDecimal netTotal = BigDecimal.ZERO;
        BigDecimal vatTotal = BigDecimal.ZERO;
        for (OrderLine line : groupLines) {
            int qty = remainingQuantity(line, orderType);
            BigDecimal unitPrice = line.getUnitPrice() != null ? line.getUnitPrice() : BigDecimal.ZERO;
            BigDecimal net = unitPrice.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal vat = net.multiply(vatRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            InvoiceLine invoiceLine = new InvoiceLine();
            invoiceLine.setInvoice(invoice);
            invoiceLine.setOrderLine(line);
            invoiceLine.setOrder(line.getOrder());
            invoiceLine.setSku(line.getProduct().getSku());
            invoiceLine.setDescription(line.getProduct().getName());
            invoiceLine.setQuantity(qty);
            invoiceLine.setUnitPrice(unitPrice);
            invoiceLine.setNetAmount(net);
            invoiceLine.setVatAmount(vat);
            invoice.getLines().add(invoiceLine);

            netTotal = netTotal.add(net);
            vatTotal = vatTotal.add(vat);
        }

        // Bill each distinct order's delivery/shipping cost as its own line,
        // once - the first invoice generated that touches an order is the
        // one that carries its delivery charge (Order.shippingInvoiced is
        // the once-only guard), whether or not every line on that order
        // made it into this particular run. Only for real invoices - an RMA
        // credit note is crediting returned goods, not refunding the
        // original outbound delivery charge.
        if (orderType != OrderType.CREDIT_REFUND) {
            List<Order> distinctOrders = groupLines.stream().map(OrderLine::getOrder).distinct().toList();
            for (Order groupOrder : distinctOrders) {
                if (groupOrder.isShippingInvoiced()) continue;
                BigDecimal shippingCost = groupOrder.getShippingCost();
                if (shippingCost == null || shippingCost.compareTo(BigDecimal.ZERO) <= 0) continue;

                BigDecimal net = shippingCost.setScale(2, RoundingMode.HALF_UP);
                BigDecimal vat = net.multiply(vatRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                InvoiceLine shippingLine = new InvoiceLine();
                shippingLine.setInvoice(invoice);
                shippingLine.setOrder(groupOrder);
                shippingLine.setSku("DELIVERY");
                shippingLine.setDescription("Delivery" + (groupOrder.getCourierMethod() != null
                        ? " - " + groupOrder.getCourierMethod() : ""));
                shippingLine.setQuantity(1);
                shippingLine.setUnitPrice(net);
                shippingLine.setNetAmount(net);
                shippingLine.setVatAmount(vat);
                shippingLine.setShipping(true);
                invoice.getLines().add(shippingLine);

                netTotal = netTotal.add(net);
                vatTotal = vatTotal.add(vat);
                groupOrder.setShippingInvoiced(true);
            }
        }

        invoice.setNetTotal(netTotal);
        invoice.setVatTotal(vatTotal);
        invoice.setGrandTotal(netTotal.add(vatTotal));
        return invoice;
    }

    /**
     * Allocates the next number from Settings > Invoicing and advances it in
     * the same call - simple read-then-write rather than a DB sequence,
     * matching the rest of this app's Settings-as-counter approach
     * elsewhere (order numbers). Fine for this app's actual concurrency (one
     * or two office staff, never simultaneously generating), and it's what
     * lets Dan change the starting point from Settings whenever OrderWise's
     * own numbering needs realigning with, per his own request.
     */
    private synchronized Integer nextInvoiceNumber() {
        int next = Integer.parseInt(settingsService.get("next_invoice_number", "1"));
        while (invoiceRepository.existsByInvoiceNumber(next)) {
            next++;
        }
        settingsService.set("next_invoice_number", String.valueOf(next + 1));
        return next;
    }

    private String savePdf(Invoice invoice, byte[] pdf) {
        try {
            String subdir = invoice.getGenerationDate().format(FILE_DATE);
            Path dir = Path.of(invoicesDir, subdir);
            Files.createDirectories(dir);
            String prefix = invoice.getInvoiceType() == InvoiceType.CREDIT_NOTE ? "CN-" : "INV-";
            Path file = dir.resolve(prefix + invoice.getInvoiceNumber() + ".pdf");
            Files.write(file, pdf);
            return file.toString();
        } catch (IOException e) {
            // Best-effort, same reasoning as the email step below - a failure
            // to save the local copy shouldn't undo an invoice that's
            // otherwise valid and (if configured) already emailed.
            log.warn("Failed to save local PDF copy for invoice {}: {}", invoice.getInvoiceNumber(), e.getMessage());
            return null;
        }
    }

    private void emailInvoice(Invoice invoice, byte[] pdf, List<String> warnings) {
        Company company = invoice.getCompany();
        String to = company.getInvoiceEmail();
        if (to == null || to.isBlank()) {
            String msg = "No invoice email on file for " + company.getName()
                    + " - PDF was generated and saved, but nothing was emailed. Add an invoice email under Companies.";
            invoice.setEmailError(msg);
            warnings.add(msg);
            return;
        }

        boolean isCredit = invoice.getInvoiceType() == InvoiceType.CREDIT_NOTE;
        String subject = (isCredit ? "Credit Note " : "Invoice ") + invoice.getInvoiceNumber()
                + " - BNS Distribution UK Ltd";
        String body = "Please see the attached " + (isCredit ? "Credit Note" : "Invoice")
                + " relating to your recent Purchase order with BNS Distribution UK Ltd. This "
                + (isCredit ? "Credit Note" : "Invoice") + " needs to be passed to the relevant individual "
                + "within your company who is responsible for processing Supplier Invoices. If you have any "
                + "discrepancies with the content of this " + (isCredit ? "Credit Note" : "Invoice")
                + ", then please notify by email to finance@bnsdistribution.co.uk";
        String filename = (isCredit ? "Credit-Note-" : "Invoice-") + invoice.getInvoiceNumber() + ".pdf";

        EmailService.SendResult result = emailService.sendWithAttachment(to, subject, body, filename, pdf, "application/pdf");
        invoice.setEmailedTo(to);
        if (result.sent()) {
            invoice.setEmailSentAt(java.time.LocalDateTime.now());
        } else {
            invoice.setEmailError(result.reason());
            warnings.add(company.getName() + ": " + result.reason());
        }
    }

    /**
     * A credit note reduces what's owed the moment it's generated (Dan:
     * "let the credits reduce what's owed once they're generated") - by
     * default that means the whole amount goes into the company's general
     * credit balance (Company.creditBalance), available immediately as
     * credit and applied to whichever invoice staff choose from Payment
     * Tracking. The one special case: a credit note raised for an RMA whose
     * replacement order has ALREADY been invoiced auto-applies straight to
     * that replacement invoice instead, since that's specifically the debt
     * it's meant to cancel out - any leftover once that invoice's paid off
     * still spills into the general credit balance. If the replacement
     * hasn't been invoiced yet, this falls back to the general balance the
     * same as any other credit (see the warning added below).
     *
     * Grouped by the ORDER each of this credit note's own lines came from
     * (not just the RMA on the group as a whole), since a CONSOLIDATED
     * company can have more than one RMA's credit lines merged onto a
     * single generated credit note in one run.
     */
    private void autoApplyRmaCredit(Invoice creditInvoice, List<String> warnings) {
        Map<Order, BigDecimal> shareByOrder = new LinkedHashMap<>();
        for (InvoiceLine line : creditInvoice.getLines()) {
            shareByOrder.merge(line.getOrder(), line.getNetAmount().add(line.getVatAmount()), BigDecimal::add);
        }

        Company company = creditInvoice.getCompany();
        for (Map.Entry<Order, BigDecimal> entry : shareByOrder.entrySet()) {
            Order creditOrderForShare = entry.getKey();
            BigDecimal share = entry.getValue();

            Optional<RmaRequest> rma = rmaRequestRepository.findByCreditOrder_Id(creditOrderForShare.getId());
            Order replacementOrder = rma.map(RmaRequest::getReplacementOrder).orElse(null);
            if (replacementOrder == null) {
                company.setCreditBalance(company.getCreditBalance().add(share));
                continue;
            }

            List<Invoice> replacementInvoices = invoiceLineRepository.findByOrder_Id(replacementOrder.getId()).stream()
                    .map(InvoiceLine::getInvoice)
                    .distinct()
                    .filter(inv -> inv.getInvoiceType() == InvoiceType.INVOICE)
                    .filter(inv -> inv.getGrandTotal().subtract(inv.getPaidAmount()).compareTo(BigDecimal.ZERO) > 0)
                    .sorted(Comparator.comparing(Invoice::getGenerationDate))
                    .toList();

            if (replacementInvoices.isEmpty()) {
                company.setCreditBalance(company.getCreditBalance().add(share));
                warnings.add("Credit note " + creditInvoice.getInvoiceNumber() + ": its RMA's replacement order "
                        + replacementOrder.getOrderNumber() + " hasn't been invoiced (or is already settled), so "
                        + "this credit went to " + company.getName() + "'s general credit balance instead - apply "
                        + "it from Payment Tracking once that invoice exists.");
                continue;
            }

            BigDecimal remaining = share;
            for (Invoice replacementInvoice : replacementInvoices) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
                BigDecimal owing = replacementInvoice.getGrandTotal().subtract(replacementInvoice.getPaidAmount());
                BigDecimal applied = remaining.min(owing);

                Payment payment = new Payment();
                payment.setInvoice(replacementInvoice);
                payment.setOrder(replacementOrder);
                payment.setAmount(applied);
                payment.setReceivedAt(LocalDateTime.now());
                payment.setReference("Credit note " + creditInvoice.getInvoiceNumber());
                payment.setNotes("Auto-applied from RMA credit note " + creditInvoice.getInvoiceNumber());
                payment.setRecordedBy("System (RMA auto-credit)");
                payment.setFromCreditBalance(true);
                paymentRepository.save(payment);

                replacementInvoice.setPaidAmount(replacementInvoice.getPaidAmount().add(applied));
                invoiceRepository.save(replacementInvoice);
                remaining = remaining.subtract(applied);
            }
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                company.setCreditBalance(company.getCreditBalance().add(remaining));
            }
        }
    }

    private int remainingQuantity(OrderLine line, OrderType orderType) {
        int target = orderType == OrderType.CREDIT_REFUND ? line.getQuantityOrdered() : line.getQuantityDespatched();
        return target - line.getQuantityInvoiced();
    }

    private OrderType orderTypeFor(InvoiceType invoiceType) {
        return invoiceType == InvoiceType.CREDIT_NOTE ? OrderType.CREDIT_REFUND : OrderType.ORDER;
    }
}
