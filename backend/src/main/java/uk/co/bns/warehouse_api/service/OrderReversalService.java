package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.bns.warehouse_api.entity.*;
import uk.co.bns.warehouse_api.enums.MovementType;
import uk.co.bns.warehouse_api.enums.OrderStatus;
import uk.co.bns.warehouse_api.enums.PickingStatus;
import uk.co.bns.warehouse_api.enums.StockItemStatus;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.exception.ValidationException;
import uk.co.bns.warehouse_api.repository.*;

import java.util.Comparator;
import java.util.List;

/**
 * Two distinct reversal operations for after despatch, matching how the
 * previous system worked - a real, everyday need (customers calling to
 * change a quantity, cancel, or change address after an order's already
 * gone through picking or despatch), not a test-data convenience.
 *
 *  - reverseToDespatch: undoes despatch only, back to the exact
 *    ready-to-pack state. Picking is deliberately left untouched - the same
 *    specific units (MACs etc.) stay allocated and packed into their
 *    cartons, so a correction (address, quantity down) doesn't mean
 *    re-picking. Re-confirm despatch once the change is made.
 *  - cancelAndReturnToStock: undoes everything, all the way back to
 *    On Hold - every allocated/despatched item genuinely returns to the
 *    bin it came from (not just "a" bin), cartons are removed, and the
 *    order is exactly as if it had just synced in. For cancellations.
 *
 * Both reconstruct each item's original bin from its own movement history
 * (the ALLOCATE/DESPATCH movement already recorded a fromLocation before
 * the location field itself got cleared) rather than guessing or falling
 * back to a default - the whole point of this feature is putting stock
 * back exactly where it really was.
 */
@Service
@RequiredArgsConstructor
public class OrderReversalService {

    private final OrderRepository orderRepository;
    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final CartonRepository cartonRepository;
    private final CartonLineRepository cartonLineRepository;
    private final InventoryService inventoryService;

    @Transactional
    public Order reverseToDespatch(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order " + orderId + " not found"));
        if (order.getStatus() != OrderStatus.COMPLETED && order.getStatus() != OrderStatus.PARTIALLY_DESPATCHED) {
            throw new ValidationException("Only a despatched order can be reversed to despatch");
        }

        List<StockItem> items = stockItemRepository.findByOrderLine_Order_Id(orderId);
        for (StockItem item : items) {
            if (item.getStatus() != StockItemStatus.DESPATCHED) continue;
            Location original = originalLocationOf(item, MovementType.DESPATCH);
            item.setStatus(StockItemStatus.ALLOCATED);
            item.setLocation(original);
            // orderLine and carton are left exactly as they are - that's the
            // whole point, nothing needs re-picking or re-packing.
            stockItemRepository.save(item);
            recordReturnMovement(item, original, MovementType.RETURN, order);
            if (original != null) {
                inventoryService.adjustInventory(item.getProduct(), original, 1);
            }
        }

        for (OrderLine line : order.getLines()) {
            line.setQuantityDespatched(0);
        }
        order.setStatus(OrderStatus.AWAITING_DESPATCH);
        return orderRepository.save(order);
    }

    @Transactional
    public Order cancelAndReturnToStock(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order " + orderId + " not found"));

        List<StockItem> items = stockItemRepository.findByOrderLine_Order_Id(orderId);
        for (StockItem item : items) {
            if (item.getStatus() != StockItemStatus.ALLOCATED && item.getStatus() != StockItemStatus.DESPATCHED) continue;

            Location original = item.getStatus() == StockItemStatus.DESPATCHED
                    ? originalLocationOf(item, MovementType.DESPATCH)
                    : item.getLocation(); // ALLOCATE never clears location, so it's already correct

            boolean wasDespatched = item.getStatus() == StockItemStatus.DESPATCHED;
            item.setStatus(StockItemStatus.AVAILABLE);
            item.setLocation(original);
            item.setOrderLine(null);
            item.setCarton(null);
            stockItemRepository.save(item);

            recordReturnMovement(item, original, MovementType.DEALLOCATE, order);
            if (original != null && wasDespatched) {
                // An allocated item was never removed from Inventory in the
                // first place (only despatch decrements it) - only add back
                // for items that had actually gone out.
                inventoryService.adjustInventory(item.getProduct(), original, 1);
            }
        }

        // carton_lines reference cartons, so they have to go first
        List<CartonLine> lines = cartonLineRepository.findByOrderLine_Order_Id(orderId);
        cartonLineRepository.deleteAll(lines);
        List<Carton> cartons = cartonRepository.findByOrder_IdOrderByCartonNumberAsc(orderId);
        cartonRepository.deleteAll(cartons);

        for (OrderLine line : order.getLines()) {
            line.setQuantityPicked(0);
            line.setQuantityDespatched(0);
        }

        order.setStatus(OrderStatus.ON_HOLD);
        order.setPickingStatus(PickingStatus.NOT_STARTED);
        order.setAcknowledgementSentAt(null);
        return orderRepository.save(order);
    }

    /**
     * Returns a specific number of already-allocated units on one order line
     * back to stock - used when editing an order reduces a line's quantity
     * below what's already been picked. Only ever touches ALLOCATED items
     * (not DESPATCHED - by the time an order reaches editing, it's already
     * been through reverseToDespatch if it needed to be, so nothing on it
     * should still be DESPATCHED). Clears any carton assignment too, in
     * either packing mode, since an item no longer on the order can't stay
     * packed into a carton for it.
     */
    @Transactional
    public void deallocateFromLine(OrderLine line, int count) {
        if (count <= 0) return;
        List<StockItem> items = stockItemRepository.findByOrderLine_Id(line.getId()).stream()
                .filter(i -> i.getStatus() == StockItemStatus.ALLOCATED)
                .sorted(Comparator.comparing(StockItem::getId))
                .limit(count)
                .toList();

        for (StockItem item : items) {
            Location original = item.getLocation(); // ALLOCATE never clears location
            item.setStatus(StockItemStatus.AVAILABLE);
            item.setOrderLine(null);
            item.setCarton(null);
            stockItemRepository.save(item);
            recordReturnMovement(item, original, MovementType.DEALLOCATE, line.getOrder());
        }

        // SPLIT-mode packing tracks quantity via CartonLine rather than a
        // direct StockItem.carton link - shrink those to match. Unassigned
        // slices (not yet packed into a real carton) are removed first;
        // only dips into an already-packed slice if genuinely necessary.
        List<CartonLine> cartonLines = cartonLineRepository.findByOrderLine_Id(line.getId());
        int toRemove = items.size();
        for (CartonLine cl : cartonLines.stream().sorted(Comparator.comparing(cl -> cl.getCarton() == null ? 0 : 1)).toList()) {
            if (toRemove <= 0) break;
            if (cl.getQuantity() <= toRemove) {
                toRemove -= cl.getQuantity();
                cartonLineRepository.delete(cl);
            } else {
                cl.setQuantity(cl.getQuantity() - toRemove);
                cartonLineRepository.save(cl);
                toRemove = 0;
            }
        }
    }

    /**
     * The bin a specific item was in immediately before the given movement
     * type last happened to it - e.g. before it was despatched. Movements are
     * looked at newest-first since an item could plausibly have more than one
     * of the same movement type in its history over time (re-picked after an
     * earlier reversal, for instance).
     */
    private Location originalLocationOf(StockItem item, MovementType type) {
        return stockMovementRepository.findByStockItem_IdOrderByCreatedAtAsc(item.getId()).stream()
                .filter(m -> m.getMovementType() == type)
                .max(Comparator.comparing(StockMovement::getCreatedAt))
                .map(StockMovement::getFromLocation)
                .orElse(item.getProduct().getDefaultLocation());
    }

    private void recordReturnMovement(StockItem item, Location toLocation, MovementType type, Order order) {
        StockMovement movement = new StockMovement();
        movement.setStockItem(item);
        movement.setProduct(item.getProduct());
        movement.setToLocation(toLocation);
        movement.setMovementType(type);
        movement.setQuantity(1);
        movement.setReference("ORDER-" + order.getOrderNumber() + "-REVERSED");
        stockMovementRepository.save(movement);
    }
}
