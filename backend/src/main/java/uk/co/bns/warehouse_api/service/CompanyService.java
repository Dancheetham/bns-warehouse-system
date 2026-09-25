package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.bns.warehouse_api.dto.CompanyRequest;
import uk.co.bns.warehouse_api.dto.CompanyView;
import uk.co.bns.warehouse_api.dto.OrderCreditStatus;
import uk.co.bns.warehouse_api.entity.Company;
import uk.co.bns.warehouse_api.entity.Invoice;
import uk.co.bns.warehouse_api.entity.Order;
import uk.co.bns.warehouse_api.entity.OrderLine;
import uk.co.bns.warehouse_api.entity.Payment;
import uk.co.bns.warehouse_api.entity.Ticket;
import uk.co.bns.warehouse_api.enums.InvoiceType;
import uk.co.bns.warehouse_api.enums.OrderStatus;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.exception.ValidationException;
import uk.co.bns.warehouse_api.repository.CompanyRepository;
import uk.co.bns.warehouse_api.repository.InvoiceRepository;
import uk.co.bns.warehouse_api.repository.OrderRepository;
import uk.co.bns.warehouse_api.repository.PaymentRepository;
import uk.co.bns.warehouse_api.repository.TicketRepository;

import java.math.BigDecimal;
import java.util.List;

/**
 * A company (B2B account) with an optional credit limit. Credit is "used" from
 * the moment an order is placed, not from despatch - matching how BNS's current
 * process already works ("Generate Invoices" produces the invoice PDF for an
 * order that's already counted against the limit). Outstanding balance is
 * computed live from orders and their payments rather than stored as a running
 * total, so it's always correct even if a payment gets corrected or an order
 * cancelled.
 */
@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final TicketRepository ticketRepository;
    private final InvoiceRepository invoiceRepository;

    public List<Company> findAll() {
        return companyRepository.findAll();
    }

    public Company findById(Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Company " + id + " not found"));
    }

    @Transactional
    public Company create(CompanyRequest request) {
        Company company = new Company();
        apply(company, request);
        return companyRepository.save(company);
    }

    @Transactional
    public Company update(Long id, CompanyRequest request) {
        Company company = findById(id);
        apply(company, request);
        return companyRepository.save(company);
    }

    /**
     * Checked up front (rather than just letting the FK constraint reject the
     * delete) so the error names exactly what's still attached and how many -
     * e.g. duplicate test/Shopify-imported companies with nothing real linked
     * to them delete cleanly, but one with real order history is protected.
     */
    @Transactional
    public void delete(Long id) {
        Company company = findById(id);
        List<Order> orders = orderRepository.findByCompany_Id(id);
        if (!orders.isEmpty()) {
            throw new ValidationException("Can't delete " + company.getName() + " - it still has "
                    + orders.size() + " order(s) linked. Unlink or reassign those orders first.");
        }
        List<Ticket> tickets = ticketRepository.findByCompany_IdOrderByCreatedAtDesc(id);
        if (!tickets.isEmpty()) {
            throw new ValidationException("Can't delete " + company.getName() + " - it still has "
                    + tickets.size() + " support ticket(s) linked. Unlink those tickets first.");
        }
        companyRepository.delete(company);
    }

    private void apply(Company company, CompanyRequest request) {
        company.setName(request.name());
        company.setCreditLimit(request.creditLimit());
        company.setShopifyCompanyId(request.shopifyCompanyId());
        company.setNotes(request.notes());
        company.setEoriNumber(request.eoriNumber());
        company.setVatNumber(request.vatNumber());
        if (request.accountNumber() != null) {
            company.setAccountNumber(request.accountNumber());
        }
        if (request.onHold() != null) {
            // Any manual edit of onHold from here is, by definition, a human
            // decision - clear autoHeld so PaymentChaserService's daily job
            // never silently reinstates or removes it again on its own.
            company.setAutoHeld(false);
            company.setOnHold(request.onHold());
        }
        if (request.doNotUse() != null) {
            company.setDoNotUse(request.doNotUse());
        }
        if (request.gaps() != null) {
            company.setGaps(request.gaps());
        }
        if (request.gdms() != null) {
            company.setGdms(request.gdms());
        }
        company.setInvoiceEmail(request.invoiceEmail());
        company.setVatRate(request.vatRate());
        if (request.invoiceGrouping() != null) {
            company.setInvoiceGrouping(request.invoiceGrouping());
        }
        company.setPaymentTermsDays(request.paymentTermsDays());
        if (request.autoHoldOnOverdue() != null) {
            company.setAutoHoldOnOverdue(request.autoHoldOnOverdue());
        }
    }

    public BigDecimal orderTotal(Order order) {
        return goodsTotal(order).add(deliveryTotal(order));
    }

    // Goods and delivery net split out separately (not just the combined
    // orderTotal above) for the invoice reporting/chart, which mirrors the
    // old OrderWise "Invoiced Values" report's own GoodsNet/DelNet columns.
    public BigDecimal goodsTotal(Order order) {
        return order.getLines().stream()
                .map(this::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal deliveryTotal(Order order) {
        return order.getShippingCost() != null ? order.getShippingCost() : BigDecimal.ZERO;
    }

    private BigDecimal lineTotal(OrderLine line) {
        if (line.getUnitPrice() == null) return BigDecimal.ZERO;
        return line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantityOrdered()));
    }

    public BigDecimal amountPaid(Long orderId) {
        return paymentRepository.findByOrder_IdOrderByReceivedAtDesc(orderId).stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Outstanding balance across every non-cancelled order for a company -
     * the "credit used" figure. Since Payment Tracking, this is three things
     * added together, so nothing is ever double-counted or falls through a
     * gap between stages:
     *   1. Orders that haven't reached Invoice Pending yet (still being
     *      picked/despatched, or on hold) - their full estimated total
     *      (pre-VAT, matching how credit was always checked before despatch).
     *   2. Despatched lines that haven't been invoiced yet - happens between
     *      despatch and someone actually running Generate Invoices. Priced
     *      off what's actually gone out (quantityDespatched), not the
     *      original order quantity.
     *   3. Every unpaid INVOICE (not credit note - those feed the credit
     *      balance instead, see toView) for this company - the real,
     *      VAT-inclusive amount actually billed and still outstanding, now
     *      including delivery/shipping (Generate Invoices bills it as its
     *      own line - see InvoiceService#buildInvoice).
     * An order's delivery charge is carried in exactly one of these buckets
     * at a time - bucket 1 pre-despatch, bucket 2 between despatch and
     * being invoiced (Order.shippingInvoiced still false), then bucket 3
     * once it's actually been billed - never double-counted or dropped.
     */
    public BigDecimal creditUsed(Long companyId) {
        BigDecimal used = BigDecimal.ZERO;

        for (Order order : orderRepository.findByCompany_Id(companyId)) {
            if (order.getStatus() == OrderStatus.CANCELLED) continue;

            if (order.getStatus() != OrderStatus.INVOICE_PENDING && order.getStatus() != OrderStatus.COMPLETED) {
                // Bucket 1 - not despatched (or not fully) yet, use the
                // pre-invoice estimate exactly as before Payment Tracking.
                BigDecimal outstanding = orderTotal(order).subtract(amountPaid(order.getId()));
                if (outstanding.compareTo(BigDecimal.ZERO) > 0) used = used.add(outstanding);
                continue;
            }
            // Bucket 2 - despatched but not yet invoiced quantity on each
            // line (usually zero once Generate Invoices has run for it),
            // plus the order's own delivery charge for as long as it
            // remains unbilled (Order.shippingInvoiced) - once Generate
            // Invoices bills it, it's covered by bucket 3 below instead.
            for (OrderLine line : order.getLines()) {
                int uninvoiced = line.getQuantityDespatched() - line.getQuantityInvoiced();
                if (uninvoiced <= 0 || line.getUnitPrice() == null) continue;
                used = used.add(line.getUnitPrice().multiply(BigDecimal.valueOf(uninvoiced)));
            }
            if (!order.isShippingInvoiced() && order.getShippingCost() != null) {
                used = used.add(order.getShippingCost());
            }
        }

        // Bucket 3 - actual unpaid invoices (credit notes excluded - those
        // reduce the credit BALANCE instead, applied in toView/creditStatusForOrder).
        for (Invoice invoice : invoiceRepository.findByCompany_IdOrderByCreatedAtDesc(companyId)) {
            if (invoice.getInvoiceType() != InvoiceType.INVOICE) continue;
            BigDecimal outstanding = invoice.getGrandTotal().subtract(invoice.getPaidAmount());
            if (outstanding.compareTo(BigDecimal.ZERO) > 0) used = used.add(outstanding);
        }

        return used;
    }

    public CompanyView toView(Company company) {
        if (company.getCreditLimit() == null) {
            return new CompanyView(company.getId(), company.getName(), null,
                    company.getShopifyCompanyId(), company.getNotes(),
                    company.getEoriNumber(), company.getVatNumber(), company.getAccountNumber(),
                    company.isOnHold(), company.isDoNotUse(), company.isGaps(), company.isGdms(),
                    null, null, false,
                    company.getInvoiceEmail(), company.getVatRate(), company.getInvoiceGrouping(),
                    company.getPaymentTermsDays(), company.isAutoHoldOnOverdue(), company.isAutoHeld(),
                    company.getCreditBalance());
        }
        BigDecimal used = creditUsed(company.getId());
        // The stored credit balance (overpayments/unapplied credit notes -
        // see Payment Tracking) counts as available straight away, which is
        // deliberately how a customer who's paid ahead can show more
        // available credit than the raw limit.
        BigDecimal available = company.getCreditLimit().subtract(used).add(company.getCreditBalance());
        return new CompanyView(company.getId(), company.getName(), company.getCreditLimit(),
                company.getShopifyCompanyId(), company.getNotes(),
                company.getEoriNumber(), company.getVatNumber(), company.getAccountNumber(),
                company.isOnHold(), company.isDoNotUse(), company.isGaps(), company.isGdms(),
                used, available, available.compareTo(BigDecimal.ZERO) < 0,
                company.getInvoiceEmail(), company.getVatRate(), company.getInvoiceGrouping(),
                company.getPaymentTermsDays(), company.isAutoHoldOnOverdue(), company.isAutoHeld(),
                company.getCreditBalance());
    }

    /** The banner shown whenever a linked order is opened. */
    public OrderCreditStatus creditStatusForOrder(Order order) {
        Company company = order.getCompany();
        if (company == null) return null;

        BigDecimal orderTotal = orderTotal(order);
        BigDecimal orderOutstanding = orderTotal.subtract(amountPaid(order.getId()));

        if (company.getCreditLimit() == null) {
            return new OrderCreditStatus(company.getId(), company.getName(), null, null, null, false,
                    orderTotal, orderOutstanding);
        }

        BigDecimal used = creditUsed(company.getId());
        BigDecimal available = company.getCreditLimit().subtract(used).add(company.getCreditBalance());
        return new OrderCreditStatus(company.getId(), company.getName(), company.getCreditLimit(), used, available,
                available.compareTo(BigDecimal.ZERO) < 0, orderTotal, orderOutstanding);
    }
}
