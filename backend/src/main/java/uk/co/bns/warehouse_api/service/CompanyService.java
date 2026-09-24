package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.bns.warehouse_api.dto.CompanyRequest;
import uk.co.bns.warehouse_api.dto.CompanyView;
import uk.co.bns.warehouse_api.dto.OrderCreditStatus;
import uk.co.bns.warehouse_api.entity.Company;
import uk.co.bns.warehouse_api.entity.Order;
import uk.co.bns.warehouse_api.entity.OrderLine;
import uk.co.bns.warehouse_api.entity.Payment;
import uk.co.bns.warehouse_api.entity.Ticket;
import uk.co.bns.warehouse_api.enums.OrderStatus;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.exception.ValidationException;
import uk.co.bns.warehouse_api.repository.CompanyRepository;
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
     * Outstanding balance across every non-cancelled order for a company - the
     * "credit used" figure. Fully paid orders naturally drop out since their
     * outstanding balance reaches zero.
     */
    public BigDecimal creditUsed(Long companyId) {
        List<Order> orders = orderRepository.findByCompany_Id(companyId);
        BigDecimal used = BigDecimal.ZERO;
        for (Order order : orders) {
            if (order.getStatus() == OrderStatus.CANCELLED) continue;
            BigDecimal outstanding = orderTotal(order).subtract(amountPaid(order.getId()));
            if (outstanding.compareTo(BigDecimal.ZERO) > 0) {
                used = used.add(outstanding);
            }
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
                    company.getInvoiceEmail(), company.getVatRate(), company.getInvoiceGrouping());
        }
        BigDecimal used = creditUsed(company.getId());
        BigDecimal available = company.getCreditLimit().subtract(used);
        return new CompanyView(company.getId(), company.getName(), company.getCreditLimit(),
                company.getShopifyCompanyId(), company.getNotes(),
                company.getEoriNumber(), company.getVatNumber(), company.getAccountNumber(),
                company.isOnHold(), company.isDoNotUse(), company.isGaps(), company.isGdms(),
                used, available, available.compareTo(BigDecimal.ZERO) < 0,
                company.getInvoiceEmail(), company.getVatRate(), company.getInvoiceGrouping());
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
        BigDecimal available = company.getCreditLimit().subtract(used);
        return new OrderCreditStatus(company.getId(), company.getName(), company.getCreditLimit(), used, available,
                available.compareTo(BigDecimal.ZERO) < 0, orderTotal, orderOutstanding);
    }
}
