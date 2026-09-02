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
import uk.co.bns.warehouse_api.enums.OrderStatus;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.repository.CompanyRepository;
import uk.co.bns.warehouse_api.repository.OrderRepository;
import uk.co.bns.warehouse_api.repository.PaymentRepository;

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

    private void apply(Company company, CompanyRequest request) {
        company.setName(request.name());
        company.setCreditLimit(request.creditLimit());
        company.setShopifyCompanyId(request.shopifyCompanyId());
        company.setNotes(request.notes());
    }

    public BigDecimal orderTotal(Order order) {
        BigDecimal total = order.getLines().stream()
                .map(this::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (order.getShippingCost() != null) {
            total = total.add(order.getShippingCost());
        }
        return total;
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
                    company.getShopifyCompanyId(), company.getNotes(), null, null, false);
        }
        BigDecimal used = creditUsed(company.getId());
        BigDecimal available = company.getCreditLimit().subtract(used);
        return new CompanyView(company.getId(), company.getName(), company.getCreditLimit(),
                company.getShopifyCompanyId(), company.getNotes(), used, available,
                available.compareTo(BigDecimal.ZERO) < 0);
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
