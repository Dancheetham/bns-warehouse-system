package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.bns.warehouse_api.dto.*;
import uk.co.bns.warehouse_api.entity.*;
import uk.co.bns.warehouse_api.enums.MovementType;
import uk.co.bns.warehouse_api.enums.OrderStatus;
import uk.co.bns.warehouse_api.enums.PickingStatus;
import uk.co.bns.warehouse_api.enums.StockItemStatus;
import uk.co.bns.warehouse_api.enums.TrackingType;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.exception.ValidationException;
import uk.co.bns.warehouse_api.repository.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One scan does what OrderWise's "Allocate by Order" + handheld "Pick" used to do as
 * two separate passes: the first scan against a line allocates AND picks the unit in
 * one transaction (StockItem AVAILABLE -> ALLOCATED, linked to the order line, logged
 * as a single movement). Packing/shipping - and the corresponding ALLOCATED -> DESPATCHED
 * transition - happens afterwards on the web GUI, not here.
 */
@Service
@RequiredArgsConstructor
public class PickingService {

    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;

    public List<OrderPickSummary> readyToPick() {
        // PARTIALLY_DESPATCHED is included alongside AWAITING_DESPATCH so an order
        // reopened for an extra shipment (new lines added to a locked/despatched
        // order - see OrderService.update()) surfaces here too: that reopen sets
        // the order to PARTIALLY_DESPATCHED and pickingStatus back to IN_PROGRESS,
        // and the new stock genuinely needs picking and despatching again.
        List<Order> orders = orderRepository.findByStatusInAndPickingStatusInOrderByOrderDateAsc(
                List.of(OrderStatus.AWAITING_DESPATCH, OrderStatus.PARTIALLY_DESPATCHED),
                List.of(PickingStatus.NOT_STARTED, PickingStatus.IN_PROGRESS));
        return orders.stream().map(this::toSummary).toList();
    }

    public PickOrderView getPickView(Long orderId) {
        Order order = findOrder(orderId);
        return toView(order);
    }

    @Transactional
    public PickOrderView start(Long orderId, PickStartRequest request) {
        Order order = findOrder(orderId);
        if (order.getStatus() != OrderStatus.AWAITING_DESPATCH && order.getStatus() != OrderStatus.PARTIALLY_DESPATCHED) {
            throw new ValidationException(
                    "Only an order that is Awaiting Despatch, or Partially Despatched with more to go, can be picked (this order is "
                            + order.getStatus() + ")");
        }
        if (order.getPickingStatus() == PickingStatus.NOT_STARTED) {
            order.setPickingStatus(PickingStatus.IN_PROGRESS);
            order.setPickingStartedAt(LocalDateTime.now());
        }
        if (request != null && request.pickedBy() != null && !request.pickedBy().isBlank()) {
            order.setPickedBy(request.pickedBy());
        }
        orderRepository.save(order);
        return toView(order);
    }

    /**
     * Scans a MAC address, serial number, or whole-batch barcode against one order
     * line. A batch scan allocates every available unit in that batch, capped at
     * however many are still required for the line - mirrors the whole-carton
     * scanning pattern already used in Goods-In.
     */
    @Transactional
    public PickScanResult scan(Long orderId, PickScanRequest request) {
        Order order = findOrder(orderId);
        OrderLine line = findLine(order, request.orderLineId());
        Product product = line.getProduct();

        int remaining = line.getQuantityOrdered() - line.getQuantityPicked();
        if (remaining <= 0) {
            throw new ValidationException("This line is already fully picked");
        }

        String code = request.code().trim();

        var byMac = stockItemRepository.findByMacAddressIgnoreCaseAndStatus(code, StockItemStatus.AVAILABLE);
        var bySerial = stockItemRepository.findBySerialNumberIgnoreCaseAndStatus(code, StockItemStatus.AVAILABLE);

        if (byMac.isPresent() || bySerial.isPresent()) {
            StockItem item = byMac.orElseGet(bySerial::get);
            if (!item.getProduct().getId().equals(product.getId())) {
                throw new ValidationException(
                        "That scan is " + item.getProduct().getSku() + ", not " + product.getSku() + " on this line");
            }
            allocateItem(item, line, request.pickedBy());
            orderRepository.save(order);
            return new PickScanResult(toView(order), List.of(item.getId()));
        }

        List<StockItem> batch = stockItemRepository.findByBatchCodeIgnoreCaseAndStatusOrderByIdAsc(code, StockItemStatus.AVAILABLE);
        if (!batch.isEmpty()) {
            long matching = batch.stream().filter(i -> i.getProduct().getId().equals(product.getId())).count();
            if (matching == 0) {
                throw new ValidationException("That batch doesn't contain " + product.getSku() + " on this line");
            }
            // A batch/carton scan is only accepted when it would consume
            // everything remaining in it - never a partial take. A sealed
            // carton is an all-or-nothing physical unit: scanning the outer
            // barcode is a reasonable substitute for scanning each item
            // individually only when the whole thing is genuinely going onto
            // this line (nothing lost for tracked products either, since each
            // StockItem taken still carries its own real MAC/serial from
            // goods-in - fewer scans, not less traceability). But if there's
            // more left in the batch than this line actually needs, accepting
            // the scan would silently grab an arbitrary subset of it - exactly
            // the "picked whichever came first" bug this replaces the fix for.
            if (matching > remaining) {
                throw new ValidationException(
                        "That batch has " + matching + " of " + product.getSku() + " remaining, but only " + remaining
                                + " needed here - scan the individual item instead, or a batch with " + remaining
                                + " or fewer left");
            }
            List<Long> allocatedIds = new java.util.ArrayList<>();
            for (StockItem item : batch) {
                if (!item.getProduct().getId().equals(product.getId())) continue;
                allocateItem(item, line, request.pickedBy());
                allocatedIds.add(item.getId());
            }
            orderRepository.save(order);
            return new PickScanResult(toView(order), allocatedIds);
        }

        throw new NotFoundException("No available stock found for scan \"" + code + "\"");
    }

    /**
     * For NONE-tracking products (no MAC/serial to scan) - takes the given quantity
     * from AVAILABLE stock, preferring the product's default bin first.
     */
    @Transactional
    public PickScanResult pickQuantity(Long orderId, PickQuantityRequest request) {
        Order order = findOrder(orderId);
        OrderLine line = findLine(order, request.orderLineId());
        Product product = line.getProduct();

        if (product.getTrackingType() != TrackingType.NONE) {
            throw new ValidationException("This product must be scanned, not entered as a quantity");
        }

        int remaining = line.getQuantityOrdered() - line.getQuantityPicked();
        if (request.quantity() > remaining) {
            throw new ValidationException("Only " + remaining + " still required on this line");
        }

        List<StockItem> candidates = product.getDefaultLocation() != null
                ? stockItemRepository.findByProduct_IdAndLocation_IdAndStatusOrderByIdAsc(
                        product.getId(), product.getDefaultLocation().getId(), StockItemStatus.AVAILABLE)
                : List.of();
        if (candidates.size() < request.quantity()) {
            candidates = stockItemRepository.findByProduct_IdAndStatusOrderByIdAsc(product.getId(), StockItemStatus.AVAILABLE);
        }
        if (candidates.size() < request.quantity()) {
            throw new ValidationException("Only " + candidates.size() + " available in stock, short of the " + request.quantity() + " requested");
        }

        List<Long> allocatedIds = new java.util.ArrayList<>();
        for (int i = 0; i < request.quantity(); i++) {
            allocateItem(candidates.get(i), line, request.pickedBy());
            allocatedIds.add(candidates.get(i).getId());
        }
        orderRepository.save(order);
        return new PickScanResult(toView(order), allocatedIds);
    }

    /**
     * Reverts a mis-scan: puts the unit back to AVAILABLE, unlinks it from the
     * order line, and logs the reversal so the audit trail shows both sides.
     */
    @Transactional
    public PickOrderView undo(Long orderId, PickUndoRequest request, String performedBy) {
        Order order = findOrder(orderId);
        StockItem item = stockItemRepository.findById(request.stockItemId())
                .orElseThrow(() -> new NotFoundException("Stock item " + request.stockItemId() + " not found"));
        if (item.getOrderLine() == null || !item.getOrderLine().getOrder().getId().equals(order.getId())) {
            throw new ValidationException("That item isn't picked against this order");
        }
        OrderLine line = item.getOrderLine();
        line.setQuantityPicked(Math.max(0, line.getQuantityPicked() - 1));
        item.setStatus(StockItemStatus.AVAILABLE);
        item.setOrderLine(null);
        stockItemRepository.save(item);
        orderLineRepository.save(line);

        StockMovement movement = new StockMovement();
        movement.setStockItem(item);
        movement.setProduct(item.getProduct());
        movement.setMovementType(MovementType.DEALLOCATE);
        movement.setQuantity(1);
        movement.setReference("ORDER-" + order.getOrderNumber());
        movement.setNotes("Pick undone");
        movement.setCreatedBy(performedBy);
        stockMovementRepository.save(movement);

        orderRepository.save(order);
        return toView(order);
    }

    /**
     * Sends the pick back to the web GUI: COMPLETE if every line hit its required
     * quantity, otherwise PARTIAL - packing picks up from either state rather than
     * blocking on a short pick.
     */
    @Transactional
    public PickOrderView complete(Long orderId, String pickedBy) {
        Order order = findOrder(orderId);
        boolean allComplete = order.getLines().stream()
                .allMatch(l -> l.getQuantityPicked() >= l.getQuantityOrdered());
        order.setPickingStatus(allComplete ? PickingStatus.COMPLETE : PickingStatus.PARTIAL);
        order.setPickingCompletedAt(LocalDateTime.now());
        if (pickedBy != null && !pickedBy.isBlank()) {
            order.setPickedBy(pickedBy);
        }
        orderRepository.save(order);
        return toView(order);
    }

    private void allocateItem(StockItem item, OrderLine line, String pickedBy) {
        item.setStatus(StockItemStatus.ALLOCATED);
        item.setOrderLine(line);
        stockItemRepository.save(item);

        line.setQuantityPicked(line.getQuantityPicked() + 1);
        orderLineRepository.save(line);

        StockMovement movement = new StockMovement();
        movement.setStockItem(item);
        movement.setProduct(item.getProduct());
        movement.setFromLocation(item.getLocation());
        movement.setMovementType(MovementType.ALLOCATE);
        movement.setQuantity(1);
        movement.setReference("ORDER-" + line.getOrder().getOrderNumber());
        movement.setCreatedBy(pickedBy);
        stockMovementRepository.save(movement);
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order " + orderId + " not found"));
    }

    private OrderLine findLine(Order order, Long lineId) {
        return order.getLines().stream()
                .filter(l -> l.getId().equals(lineId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Order line " + lineId + " not found on this order"));
    }

    private OrderPickSummary toSummary(Order order) {
        return new OrderPickSummary(
                order.getId(), order.getOrderNumber(), order.getCustomerName(), order.getOrderDate(),
                order.getLines().size(), order.getPickingStatus(), order.getPickedBy());
    }

    private PickOrderView toView(Order order) {
        List<PickLineView> lines = order.getLines().stream().map(line -> {
            Product product = line.getProduct();
            String defaultBinCode = product.getDefaultLocation() != null ? product.getDefaultLocation().getCode() : null;
            int defaultBinAvailable = product.getDefaultLocation() != null
                    ? stockItemRepository.findByProduct_IdAndLocation_IdAndStatusOrderByIdAsc(
                            product.getId(), product.getDefaultLocation().getId(), StockItemStatus.AVAILABLE).size()
                    : 0;
            int totalAvailable = stockItemRepository.findByProduct_IdAndStatusOrderByIdAsc(product.getId(), StockItemStatus.AVAILABLE).size();
            boolean complete = line.getQuantityPicked() >= line.getQuantityOrdered();
            boolean shortPicked = order.getPickingStatus() == PickingStatus.PARTIAL && !complete;
            return new PickLineView(
                    line.getId(), product.getId(), product.getSku(), product.getName(),
                    defaultBinCode, defaultBinAvailable, totalAvailable,
                    line.getQuantityOrdered(), line.getQuantityPicked(),
                    product.getTrackingType() != TrackingType.NONE, complete, shortPicked);
        }).toList();

        return new PickOrderView(order.getId(), order.getOrderNumber(), order.getCustomerName(),
                order.getPickingStatus(), order.getPickedBy(), lines);
    }
}
