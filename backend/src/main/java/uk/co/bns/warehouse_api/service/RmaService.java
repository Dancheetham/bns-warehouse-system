package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.bns.warehouse_api.dto.*;
import uk.co.bns.warehouse_api.entity.*;
import uk.co.bns.warehouse_api.enums.*;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.exception.ValidationException;
import uk.co.bns.warehouse_api.repository.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Replaces the emailed Excel RMA template with a public form (RmaLookupService
 * does the auto-matching) plus a small internal pipeline:
 *
 *  SUBMITTED -> APPROVED   assigns the real RMA number; if any item is faulty,
 *                          creates an ON_HOLD replacement order for it (delivery
 *                          details required in that case)
 *           -> REJECTED
 *  APPROVED -> RECEIVED    physical return processed - stock booked back in
 *                          (QUARANTINED if faulty, AVAILABLE otherwise), and one
 *                          CREDIT_REFUND order created covering everything
 *                          credited: faulty items at the price they were
 *                          originally sold/replaced at, non-faulty items at that
 *                          price less RSF if the resale checks failed.
 */
@Service
@RequiredArgsConstructor
public class RmaService {

    private final RmaRequestRepository rmaRequestRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final RmaLookupService lookupService;

    private static final BigDecimal RSF_RATE = new BigDecimal("0.85");

    @Transactional
    public RmaRequest submit(RmaSubmissionRequest request) {
        RmaRequest rma = new RmaRequest();
        rma.setCustomerName(request.customerName());
        rma.setCustomerCompany(request.customerCompany());
        rma.setCustomerAddress(request.customerAddress());
        rma.setContactName(request.contactName());
        rma.setContactPhone(request.contactPhone());
        rma.setContactEmail(request.contactEmail());
        rma.setDeliveryName(request.deliveryName());
        rma.setDeliveryTown(request.deliveryTown());
        rma.setDeliveryCountry(request.deliveryCountry());
        rma.setDeliveryPostcode(request.deliveryPostcode());
        rma.setDeliveryCountryCode(request.deliveryCountryCode());
        rma.setPublicReference("PENDING");
        rma = rmaRequestRepository.save(rma);

        for (RmaItemSubmission itemSubmission : request.items()) {
            Product product = productRepository.findById(itemSubmission.productId())
                    .orElseThrow(() -> new NotFoundException("Product " + itemSubmission.productId() + " not found"));

            RmaLookupResult lookup = lookupService.lookup(itemSubmission.identifier(), itemSubmission.faulty());

            RmaItem item = new RmaItem();
            item.setRmaRequest(rma);
            item.setProduct(product);
            item.setIdentifier(itemSubmission.identifier());
            item.setQuantity(itemSubmission.quantity());
            item.setFaulty(itemSubmission.faulty());
            item.setGrandstreamTicketNumber(itemSubmission.grandstreamTicketNumber());
            item.setReasonForReturn(itemSubmission.reasonForReturn());

            if (lookup.orderMatched()) {
                Order matchedOrder = orderRepository.findById(lookup.orderId()).orElse(null);
                item.setMatchedOrder(matchedOrder);
                item.setMatchedUnitPrice(lookup.unitPrice());
                item.setReturnWindowExpiresAt(lookup.returnWindowExpiresAt());
                if (rma.getOriginalOrder() == null) {
                    rma.setOriginalOrder(matchedOrder);
                }
            }
            rma.getItems().add(item);
        }

        rma.setPublicReference("RMAREQ-" + String.format("%05d", rma.getId()));
        return rmaRequestRepository.save(rma);
    }

    public List<RmaRequest> listByStatus(RmaStatus status) {
        return status != null
                ? rmaRequestRepository.findByStatusOrderBySubmittedAtAsc(status)
                : rmaRequestRepository.findAll();
    }

    public RmaRequest getDetail(Long id) {
        return rmaRequestRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("RMA " + id + " not found"));
    }

    @Transactional
    public RmaRequest approve(Long id, ApproveRmaRequest request) {
        RmaRequest rma = getDetail(id);
        if (rma.getStatus() != RmaStatus.SUBMITTED) {
            throw new ValidationException("Only a submitted RMA can be approved (this one is " + rma.getStatus() + ")");
        }

        boolean anyFaulty = rma.getItems().stream().anyMatch(RmaItem::getFaulty);
        if (anyFaulty) {
            if (isBlank(request.deliveryName()) || isBlank(request.deliveryTown()) || isBlank(request.deliveryPostcode())) {
                throw new ValidationException(
                        "Delivery details are required to approve this RMA - it includes a faulty item needing a replacement shipped");
            }
            rma.setDeliveryName(request.deliveryName());
            rma.setDeliveryTown(request.deliveryTown());
            rma.setDeliveryCountry(request.deliveryCountry());
            rma.setDeliveryPostcode(request.deliveryPostcode());
            rma.setDeliveryCountryCode(request.deliveryCountryCode());
        }

        rma.setRmaNumber("RMA" + String.format("%04d", rma.getId()));

        if (anyFaulty) {
            Order replacement = new Order();
            replacement.setOrderNumber(generateOrderNumber());
            replacement.setOrderDate(LocalDateTime.now());
            replacement.setCustomerName(rma.getCustomerName());
            replacement.setCustomerEmail(rma.getContactEmail());
            replacement.setOrderReference(rma.getRmaNumber());
            replacement.setDeliveryName(rma.getDeliveryName());
            replacement.setDeliveryTown(rma.getDeliveryTown());
            replacement.setDeliveryCountry(rma.getDeliveryCountry());
            replacement.setDeliveryPostcode(rma.getDeliveryPostcode());
            replacement.setDeliveryCountryCode(rma.getDeliveryCountryCode());
            replacement.setStatus(OrderStatus.ON_HOLD);
            replacement.setOrderType(OrderType.ORDER);

            for (RmaItem item : rma.getItems()) {
                if (!item.getFaulty()) continue;
                OrderLine line = new OrderLine();
                line.setOrder(replacement);
                line.setProduct(item.getProduct());
                line.setQuantityOrdered(item.getQuantity());
                line.setUnitPrice(item.getMatchedUnitPrice());
                line.setNotes("RMA advance replacement" + (item.getIdentifier() != null ? " - " + item.getIdentifier() : ""));
                replacement.getLines().add(line);
            }
            replacement = orderRepository.save(replacement);
            rma.setReplacementOrder(replacement);
        }

        rma.setStatus(RmaStatus.APPROVED);
        rma.setApprovedAt(LocalDateTime.now());
        rma.setApprovedBy(request.approvedBy());
        return rmaRequestRepository.save(rma);
    }

    @Transactional
    public RmaRequest reject(Long id, RejectRmaRequest request) {
        RmaRequest rma = getDetail(id);
        if (rma.getStatus() != RmaStatus.SUBMITTED) {
            throw new ValidationException("Only a submitted RMA can be rejected (this one is " + rma.getStatus() + ")");
        }
        rma.setStatus(RmaStatus.REJECTED);
        rma.setRejectedAt(LocalDateTime.now());
        rma.setRejectedBy(request.rejectedBy());
        rma.setRejectionReason(request.reason());
        return rmaRequestRepository.save(rma);
    }

    @Transactional
    public RmaRequest receive(Long id, ReceiveRmaRequest request) {
        RmaRequest rma = getDetail(id);
        if (rma.getStatus() != RmaStatus.APPROVED) {
            throw new ValidationException("Only an approved RMA can be received (this one is " + rma.getStatus() + ")");
        }

        Map<Long, ReceiveRmaItemInput> inputsById = request.items().stream()
                .collect(Collectors.toMap(ReceiveRmaItemInput::rmaItemId, i -> i));

        Order creditOrder = new Order();
        creditOrder.setOrderNumber(generateOrderNumber());
        creditOrder.setOrderDate(LocalDateTime.now());
        creditOrder.setCustomerName(rma.getCustomerName());
        creditOrder.setCustomerEmail(rma.getContactEmail());
        creditOrder.setOrderReference(rma.getRmaNumber());
        creditOrder.setStatus(OrderStatus.COMPLETED);
        creditOrder.setOrderType(OrderType.CREDIT_REFUND);

        for (RmaItem item : rma.getItems()) {
            ReceiveRmaItemInput input = inputsById.get(item.getId());
            if (input == null) continue;

            item.setGrandstreamWarrantyChecked(input.grandstreamWarrantyChecked());
            if (!input.received()) continue;

            item.setReceived(true);
            item.setRsfApplied(!item.getFaulty() && input.rsfApplied());

            // The unit's original StockItem row is still there (despatch only
            // clears location/status, never the MAC/serial - see DespatchService)
            // and that MAC/serial is still uniquely constrained, so a returning
            // unit must revive that row rather than insert a fresh one with the
            // same identifier.
            StockItem stockItem = findExistingStockItem(item)
                    .orElseGet(StockItem::new);
            stockItem.setProduct(item.getProduct());
            if (item.getProduct().getTrackingType() == TrackingType.MAC) {
                stockItem.setMacAddress(item.getIdentifier());
            } else if (item.getProduct().getTrackingType() == TrackingType.SERIAL) {
                stockItem.setSerialNumber(item.getIdentifier());
            }
            stockItem.setStatus(item.getFaulty() ? StockItemStatus.QUARANTINED : StockItemStatus.AVAILABLE);
            stockItem.setQuarantined(item.getFaulty());
            stockItem.setQuarantineReason(item.getFaulty() ? "RMA " + rma.getRmaNumber() + " - pending Grandstream processing" : null);
            stockItem.setLocation(null);
            stockItem.setReceivedAt(LocalDateTime.now());
            stockItemRepository.save(stockItem);

            StockMovement movement = new StockMovement();
            movement.setStockItem(stockItem);
            movement.setProduct(item.getProduct());
            movement.setMovementType(MovementType.RETURN);
            movement.setQuantity(item.getQuantity());
            movement.setReference(rma.getRmaNumber());
            movement.setNotes(item.getFaulty() ? "RMA return - faulty" : "RMA return - for credit");
            stockMovementRepository.save(movement);

            if (item.getMatchedUnitPrice() != null) {
                BigDecimal price = item.getFaulty() || !item.getRsfApplied()
                        ? item.getMatchedUnitPrice()
                        : item.getMatchedUnitPrice().multiply(RSF_RATE);

                OrderLine creditLine = new OrderLine();
                creditLine.setOrder(creditOrder);
                creditLine.setProduct(item.getProduct());
                creditLine.setQuantityOrdered(item.getQuantity());
                creditLine.setUnitPrice(price);
                creditLine.setNotes((item.getFaulty() ? "RMA replacement credit" : "RMA return credit")
                        + (item.getRsfApplied() ? " (15% RSF applied)" : ""));
                creditOrder.getLines().add(creditLine);
            }
            item.setCredited(true);
        }

        if (!creditOrder.getLines().isEmpty()) {
            creditOrder = orderRepository.save(creditOrder);
            rma.setCreditOrder(creditOrder);
        }

        rma.setStatus(RmaStatus.RECEIVED);
        rma.setReceivedAt(LocalDateTime.now());
        rma.setReceivedBy(request.receivedBy());
        return rmaRequestRepository.save(rma);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private Optional<StockItem> findExistingStockItem(RmaItem item) {
        if (item.getProduct().getTrackingType() == TrackingType.MAC) {
            return stockItemRepository.findByMacAddressIgnoreCase(item.getIdentifier());
        }
        if (item.getProduct().getTrackingType() == TrackingType.SERIAL) {
            return stockItemRepository.findBySerialNumberIgnoreCase(item.getIdentifier());
        }
        return Optional.empty();
    }

    private String generateOrderNumber() {
        long count = orderRepository.count() + 1;
        return "SO-" + (10000 + count);
    }

    public RmaSummaryView toSummary(RmaRequest rma) {
        boolean anyUnmatched = rma.getItems().stream().anyMatch(i -> i.getMatchedOrder() == null);
        boolean anyFaulty = rma.getItems().stream().anyMatch(RmaItem::getFaulty);
        return new RmaSummaryView(rma.getId(), rma.getPublicReference(), rma.getRmaNumber(), rma.getStatus(),
                rma.getCustomerName(), rma.getSubmittedAt(), rma.getItems().size(), anyUnmatched, anyFaulty);
    }

    public RmaDetailView toDetail(RmaRequest rma) {
        List<RmaItemView> items = rma.getItems().stream().map(this::toItemView).toList();
        return new RmaDetailView(
                rma.getId(), rma.getPublicReference(), rma.getRmaNumber(), rma.getStatus(),
                rma.getCustomerName(), rma.getCustomerCompany(), rma.getCustomerAddress(),
                rma.getContactName(), rma.getContactPhone(), rma.getContactEmail(),
                rma.getDeliveryName(), rma.getDeliveryTown(), rma.getDeliveryCountry(),
                rma.getDeliveryPostcode(), rma.getDeliveryCountryCode(),
                rma.getOriginalOrder() != null ? rma.getOriginalOrder().getId() : null,
                rma.getOriginalOrder() != null ? rma.getOriginalOrder().getOrderNumber() : null,
                rma.getReplacementOrder() != null ? rma.getReplacementOrder().getId() : null,
                rma.getReplacementOrder() != null ? rma.getReplacementOrder().getOrderNumber() : null,
                rma.getCreditOrder() != null ? rma.getCreditOrder().getId() : null,
                rma.getCreditOrder() != null ? rma.getCreditOrder().getOrderNumber() : null,
                rma.getNotes(), rma.getSubmittedAt(),
                rma.getApprovedAt(), rma.getApprovedBy(),
                rma.getRejectedAt(), rma.getRejectedBy(), rma.getRejectionReason(),
                rma.getReceivedAt(), rma.getReceivedBy(),
                items);
    }

    private RmaItemView toItemView(RmaItem item) {
        boolean windowValid = item.getReturnWindowExpiresAt() != null
                && !item.getReturnWindowExpiresAt().isBefore(java.time.LocalDate.now());
        return new RmaItemView(
                item.getId(), item.getProduct().getId(), item.getProduct().getSku(), item.getProduct().getName(),
                item.getIdentifier(), item.getQuantity(), item.getFaulty(), item.getGrandstreamTicketNumber(),
                item.getReasonForReturn(),
                item.getMatchedOrder() != null ? item.getMatchedOrder().getId() : null,
                item.getMatchedOrder() != null ? item.getMatchedOrder().getOrderNumber() : null,
                item.getMatchedUnitPrice(), item.getReturnWindowExpiresAt(), windowValid,
                item.getGrandstreamWarrantyChecked(), item.getReceived(), item.getRsfApplied(), item.getCredited());
    }
}
