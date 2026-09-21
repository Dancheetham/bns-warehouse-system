package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.bns.warehouse_api.dto.OrderCreditStatus;
import uk.co.bns.warehouse_api.dto.OrderLineRequest;
import uk.co.bns.warehouse_api.dto.OrderRequest;
import uk.co.bns.warehouse_api.entity.Order;
import uk.co.bns.warehouse_api.entity.OrderLine;
import uk.co.bns.warehouse_api.entity.Product;
import uk.co.bns.warehouse_api.enums.PickingStatus;
import uk.co.bns.warehouse_api.exception.ConflictException;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.repository.OrderRepository;
import uk.co.bns.warehouse_api.repository.ProductRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CompanyService companyService;
    private final OrderReversalService orderReversalService;

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    public Order findById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order " + id + " not found"));
    }

    /**
     * A brand-new order has no picked stock to worry about - clear and
     * recreate is simple and correct here.
     */
    @Transactional
    public Order create(OrderRequest request) {
        Order order = new Order();
        String orderNumber = request.orderNumber() != null && !request.orderNumber().isBlank()
                ? request.orderNumber()
                : generateOrderNumber();

        if (orderRepository.existsByOrderNumber(orderNumber)) {
            throw new ConflictException("An order with number " + orderNumber + " already exists");
        }
        order.setOrderNumber(orderNumber);
        applyFields(order, request);
        applyLines(order, request.lines());
        return orderRepository.save(order);
    }

    /**
     * An existing order may already have picked stock (StockItems pointing
     * at its lines) - naively clearing and recreating lines, like create()
     * does, fails outright with a foreign key violation the moment that's
     * true. reconcileLines diffs against what's actually changed instead:
     * an unchanged or increased quantity leaves picked stock completely
     * alone (nothing needs re-picking); a decreased quantity returns the
     * excess specific units to stock via OrderReversalService rather than
     * leaving them allocated to nothing; a removed line does the same for
     * everything on it before the line itself goes.
     */
    @Transactional
    public Order update(Long id, OrderRequest request) {
        Order order = findById(id);
        // Comparing explicitly against the version this edit actually
        // started from, rather than relying on Hibernate's own automatic
        // @Version check - manually setting an entity's version field once
        // it's already loaded/managed is explicitly unsupported by JPA, and
        // in practice Hibernate just ignores it rather than using it for the
        // update's WHERE clause. This check is deterministic and doesn't
        // depend on any of that: if the version this edit started from
        // doesn't match what's actually in the database right now, someone
        // else has saved in between.
        if (request.version() != null && !request.version().equals(order.getVersion())) {
            throw new ConflictException(
                    "This was changed by someone else while you had it open - reload the page to see their changes, then try your edit again");
        }
        applyFields(order, request);
        reconcileLines(order, request.lines());
        recomputePickingStatusIfMoreNeeded(order);
        return orderRepository.save(order);
    }

    private void applyFields(Order order, OrderRequest request) {
        order.setOrderDate(request.orderDate());
        order.setCustomerName(request.customerName());
        order.setCustomerEmail(request.customerEmail());
        order.setOrderReference(request.orderReference());
        order.setEcommerceOrderNumber(request.ecommerceOrderNumber());
        order.setOrderedBy(request.orderedBy());
        order.setDeliveryName(request.deliveryName());
        order.setDeliveryTown(request.deliveryTown());
        order.setDeliveryCountry(request.deliveryCountry());
        order.setDeliveryPostcode(request.deliveryPostcode());
        order.setDeliveryCountryCode(request.deliveryCountryCode());
        order.setStatus(request.status());
        order.setOrderType(request.orderType());
        order.setShippingCost(request.shippingCost());
        order.setCourierMethod(request.courierMethod());
        order.setSpecialInstructions(request.specialInstructions());
        order.setCompany(request.companyId() != null ? companyService.findById(request.companyId()) : null);
    }

    /**
     * Replaces the old multi-screen "untick On Hold" flow: one call, sets shipping
     * cost and courier if provided, and moves the order straight to AWAITING_DESPATCH.
     *
     * If the order's company is over its credit limit, release is blocked unless
     * explicitly overridden - matching "block at release" rather than at Shopify
     * checkout, since that's what the existing OrderWise process already does.
     * An override requires a reason, which is written to the server log for an
     * audit trail (there's no dedicated audit-log table yet).
     */
    @Transactional
    public Order releaseForDespatch(Long id, java.math.BigDecimal shippingCost, String courierMethod,
                                     boolean overrideCreditHold, String overrideReason) {
        Order order = findById(id);
        if (order.getStatus() != uk.co.bns.warehouse_api.enums.OrderStatus.ON_HOLD) {
            throw new uk.co.bns.warehouse_api.exception.ValidationException(
                    "Only an order that is On Hold can be released for despatch (this order is " + order.getStatus() + ")");
        }

        OrderCreditStatus creditStatus = companyService.creditStatusForOrder(order);
        if (creditStatus != null && creditStatus.overLimit()) {
            if (!overrideCreditHold) {
                throw new uk.co.bns.warehouse_api.exception.ValidationException(
                        creditStatus.companyName() + " is over its credit limit ("
                                + creditStatus.creditUsed() + " used of " + creditStatus.creditLimit()
                                + ") - release anyway with a reason to override.");
            }
            log.warn("Order {} released over credit limit for company {} ({} used of {}) - override reason: {}",
                    order.getOrderNumber(), creditStatus.companyName(), creditStatus.creditUsed(),
                    creditStatus.creditLimit(), overrideReason);
        }

        if (shippingCost != null) {
            order.setShippingCost(shippingCost);
        }
        if (courierMethod != null && !courierMethod.isBlank()) {
            order.setCourierMethod(courierMethod);
        }
        order.setStatus(uk.co.bns.warehouse_api.enums.OrderStatus.AWAITING_DESPATCH);
        return orderRepository.save(order);
    }

    private void applyLines(Order order, List<OrderLineRequest> lineRequests) {
        List<OrderLine> lines = new ArrayList<>();
        for (OrderLineRequest lr : lineRequests) {
            Product product = productRepository.findById(lr.productId())
                    .orElseThrow(() -> new NotFoundException("Product " + lr.productId() + " not found"));
            OrderLine line = new OrderLine();
            line.setOrder(order);
            line.setProduct(product);
            line.setQuantityOrdered(lr.quantityOrdered());
            line.setQuantityDespatched(lr.quantityDespatched() != null ? lr.quantityDespatched() : 0);
            line.setUnitPrice(lr.unitPrice());
            line.setNotes(lr.notes());
            lines.add(line);
        }
        order.getLines().addAll(lines);
    }

    /**
     * Matches existing lines to the new request by product (the request
     * carries no line id - there's normally only one line per product on an
     * order, and if there happens to be more than one for the same product,
     * they're paired up in order rather than treated as ambiguous). A line
     * whose quantity drops below what's already been picked returns the
     * excess to stock before the quantity itself changes; a line missing
     * from the new request does the same for everything on it before being
     * removed. A line that's new or unpicked needs nothing beyond the plain
     * field updates applyLines already does for a brand-new order.
     */
    private void reconcileLines(Order order, List<OrderLineRequest> lineRequests) {
        List<OrderLine> existingLines = new ArrayList<>(order.getLines());
        List<OrderLine> keptLines = new ArrayList<>();

        for (OrderLineRequest lr : lineRequests) {
            Product product = productRepository.findById(lr.productId())
                    .orElseThrow(() -> new NotFoundException("Product " + lr.productId() + " not found"));

            OrderLine existing = existingLines.stream()
                    .filter(l -> l.getProduct().getId().equals(product.getId()))
                    .findFirst()
                    .orElse(null);

            if (existing != null) {
                existingLines.remove(existing);
                int newQty = lr.quantityOrdered();
                if (existing.getQuantityPicked() > newQty) {
                    orderReversalService.deallocateFromLine(existing, existing.getQuantityPicked() - newQty);
                    existing.setQuantityPicked(newQty);
                }
                existing.setQuantityOrdered(newQty);
                existing.setQuantityDespatched(lr.quantityDespatched() != null ? lr.quantityDespatched() : existing.getQuantityDespatched());
                existing.setUnitPrice(lr.unitPrice());
                existing.setNotes(lr.notes());
                keptLines.add(existing);
            } else {
                OrderLine line = new OrderLine();
                line.setOrder(order);
                line.setProduct(product);
                line.setQuantityOrdered(lr.quantityOrdered());
                line.setQuantityDespatched(lr.quantityDespatched() != null ? lr.quantityDespatched() : 0);
                line.setUnitPrice(lr.unitPrice());
                line.setNotes(lr.notes());
                keptLines.add(line);
            }
        }

        // Anything left over wasn't in the new request at all.
        for (OrderLine removed : existingLines) {
            if (removed.getQuantityPicked() > 0) {
                orderReversalService.deallocateFromLine(removed, removed.getQuantityPicked());
            }
        }

        order.getLines().clear();
        order.getLines().addAll(keptLines);
    }

    /**
     * Only ever pushes picking status forward into IN_PROGRESS when an edit
     * has genuinely left a line short of what's needed - never touches it
     * otherwise, since COMPLETE/PARTIAL/NOT_STARTED are all still accurate
     * as they are if nothing outstanding needs picking. Once IN_PROGRESS,
     * the order naturally drops off the packing-ready list and reappears on
     * the handheld showing just the extra quantity needed - picking's own
     * existing "Complete Pick" action moves it back to COMPLETE/PARTIAL (and
     * therefore back onto the packing-ready list) exactly as it always has.
     */
    private void recomputePickingStatusIfMoreNeeded(Order order) {
        boolean anyLineShort = order.getLines().stream()
                .anyMatch(l -> l.getQuantityPicked() < l.getQuantityOrdered());
        if (anyLineShort && order.getPickingStatus() != PickingStatus.NOT_STARTED) {
            order.setPickingStatus(PickingStatus.IN_PROGRESS);
        }
    }

    @Transactional
    public Order markAcknowledged(Long id) {
        Order order = findById(id);
        order.setAcknowledgementSentAt(java.time.LocalDateTime.now());
        return orderRepository.save(order);
    }

    /**
     * Was "SO-" + (10000 + count()) - fine as long as order numbers exactly
     * tracked the row count with no gaps, which breaks the moment any order was
     * ever manually numbered, deleted, or otherwise out of step (as happened
     * here: 4 existing orders meant count()+1 recomputed "SO-10005", which
     * already existed, and every scheduled Shopify order sync kept retrying
     * that exact same collision forever). Now actually checks for a free
     * number rather than assuming one.
     */
    public String generateOrderNumber() {
        long candidate = orderRepository.count() + 1;
        String orderNumber;
        do {
            orderNumber = "SO-" + (10000 + candidate);
            candidate++;
        } while (orderRepository.existsByOrderNumber(orderNumber));
        return orderNumber;
    }

    public OrderCreditStatus getCreditStatus(Long id) {
        return companyService.creditStatusForOrder(findById(id));
    }
}
