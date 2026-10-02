package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.bns.warehouse_api.dto.OrderReversalResult;
import uk.co.bns.warehouse_api.entity.*;
import uk.co.bns.warehouse_api.enums.MovementType;
import uk.co.bns.warehouse_api.enums.OrderStatus;
import uk.co.bns.warehouse_api.enums.PickingStatus;
import uk.co.bns.warehouse_api.enums.StockItemStatus;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.exception.ValidationException;
import uk.co.bns.warehouse_api.repository.*;

import java.util.ArrayList;
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
 *
 * Since v0.142, both also attempt to void the actual courier booking, not
 * just BNS's own record of it - see cancelApcShipmentIfBooked() for why
 * that's APC-only (DPD has no cancel/void endpoint) and best-effort (never
 * blocks the stock reversal itself). Both methods now return an
 * OrderReversalResult (the reversed order plus an optional courier
 * warning) rather than a bare Order.
 */
@Service
@RequiredArgsConstructor
public class OrderReversalService {

    private static final Logger log = LoggerFactory.getLogger(OrderReversalService.class);

    private final OrderRepository orderRepository;
    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final CartonRepository cartonRepository;
    private final CartonLineRepository cartonLineRepository;
    private final InventoryService inventoryService;
    private final GdmsRecallService gdmsRecallService;
    private final ApcShippingService apcShippingService;
    private final uk.co.bns.warehouse_api.repository.DeliveryRepository deliveryRepository;

    @Transactional
    public OrderReversalResult reverseToDespatch(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order " + orderId + " not found"));
        if (order.getStatus() != OrderStatus.COMPLETED && order.getStatus() != OrderStatus.PARTIALLY_DESPATCHED
                && order.getStatus() != OrderStatus.INVOICE_PENDING) {
            throw new ValidationException("Only a despatched order can be reversed to despatch");
        }
        // Once any part of this order has actually gone out on a generated
        // invoice/credit note, the stock and the invoice would fall out of
        // sync if reversed - a real invoice was already issued for it.
        // Raise a credit note (RMA) to correct an invoiced order instead.
        boolean anyInvoiced = order.getLines().stream().anyMatch(l -> l.getQuantityInvoiced() > 0);
        if (anyInvoiced) {
            throw new ValidationException(
                    "This order has already been invoiced (in full or in part) - reversing shipped stock isn't "
                            + "supported once invoiced. Raise a credit note through RMA instead.");
        }

        // DPD's booking is genuinely undone here, on our side - clearing
        // dpdShipmentId is what makes bookDpdShipment() at the next despatch
        // confirmation treat this as a fresh booking again rather than its
        // "already booked" guard silently skipping it and leaving the
        // corrected order pointing at the stale shipment (wrong weight/
        // parcel count/network, and whatever was printed off it) from
        // before this reversal. DPD's own documented API has no cancel/void
        // endpoint to call here (checked - only create shipment, fetch
        // labels, and reference lookups exist), so the old shipment/labels
        // still technically exist on DPD's side, uncancelled - if that
        // matters (e.g. it was already scanned in for collection), it needs
        // cancelling from DPD's own portal (myDPD), not from here.
        //
        // dpdNetworkKey (the chosen Service) is deliberately NOT cleared here,
        // unlike the fields above - it's the staff member's explicit choice,
        // not a DPD booking artefact, and resolveNetworkCode() treats it as
        // authoritative. Clearing it used to mean the next despatch found no
        // order choice and silently auto-picked whatever DPD's live lookup
        // returned first (often not what was on the order before), instead
        // of re-booking against the exact same service as before.
        order.setDpdShipmentId(null);
        order.setDpdConsignmentNumber(null);
        order.setDpdParcelNumbers(null);
        order.setDpdShippedAt(null);

        // APC equivalent of the DPD clearing above - but unlike DPD, APC's
        // API does have a cancel endpoint (CancelOrder, guide section 7), so
        // this attempts a real void before clearing the waybill, rather than
        // just leaving it uncancelled on APC's side. apcServiceCode is
        // deliberately left alone, same as dpdNetworkKey above.
        String courierWarning = cancelApcShipmentIfBooked(order);
        order.setApcOrderNumber(null);
        order.setApcWaybill(null);
        order.setApcShippedAt(null);

        List<StockItem> items = stockItemRepository.findByOrderLine_Order_Id(orderId);
        List<StockItem> reversedItems = new ArrayList<>();
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
            reversedItems.add(item);
        }

        // Any of these that had already been synced to GDMS need recalling
        // and their gdmsSyncedAt reset, so a future re-despatch resyncs them
        // fresh rather than being silently skipped as "already synced" - see
        // GdmsRecallService for the full reasoning (best-effort, never blocks
        // this reversal itself).
        gdmsRecallService.recallSyncedItems(reversedItems, order.getOrderNumber(), "Auto (Reverse to Despatch)");

        for (OrderLine line : order.getLines()) {
            line.setQuantityDespatched(0);
        }
        order.setStatus(OrderStatus.AWAITING_DESPATCH);
        // Whatever Delivery row(s) this order had recorded it genuinely
        // weren't final deliveries after all - see Delivery.java/Dan's
        // reasoning (2026-10-02): both reversal actions are only ever usable
        // before an order's actually, irreversibly out the door, so a
        // delivery being reversed here was never a real one for Delivery
        // History's purposes.
        deliveryRepository.deleteByOrder_Id(orderId);
        return new OrderReversalResult(orderRepository.save(order), courierWarning);
    }

    @Transactional
    public OrderReversalResult cancelAndReturnToStock(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order " + orderId + " not found"));

        List<StockItem> items = stockItemRepository.findByOrderLine_Order_Id(orderId);
        List<StockItem> returnedItems = new ArrayList<>();
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
            returnedItems.add(item);
        }

        // Same reasoning as reverseToDespatch() - recall+reset any of these
        // that were already synced to GDMS. order.getOrderNumber() is still
        // valid here even though item.setOrderLine(null) above cleared each
        // item's own link back to it.
        gdmsRecallService.recallSyncedItems(returnedItems, order.getOrderNumber(), "Auto (Cancelled)");

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
        // Same reasoning as reverseToDespatch() above - without this, an
        // order cancelled and re-released would still carry its old DPD
        // shipment reference, and bookDpdShipment()'s "already booked" guard
        // would silently skip booking a real one for whatever it's used for
        // next time round.
        order.setDpdShipmentId(null);
        order.setDpdConsignmentNumber(null);
        order.setDpdParcelNumbers(null);
        order.setDpdShippedAt(null);
        order.setDpdNetworkKey(null);
        // APC equivalent - attempts a real void via CancelOrder first, same
        // as reverseToDespatch() above.
        String courierWarning = cancelApcShipmentIfBooked(order);
        order.setApcOrderNumber(null);
        order.setApcWaybill(null);
        order.setApcShippedAt(null);
        order.setApcServiceCode(null);
        // Same reasoning as reverseToDespatch() above.
        deliveryRepository.deleteByOrder_Id(orderId);
        return new OrderReversalResult(orderRepository.save(order), courierWarning);
    }

    /**
     * Best-effort void of an already-booked APC shipment, called right
     * before its waybill/order-number are cleared in both reversal methods
     * above. DPD has no equivalent call - its documented API has no
     * cancel/void endpoint at all (checked again 2026-10-01 via DPD's own
     * docs portal: Shipping, Collections, Sender Actions, Receiver Actions
     * and Pickup sections cover booking, labels, tracking, delivery
     * redirection and driver-attended pickups, but nothing to cancel a
     * shipment/label itself - "Cancel Collection" only cancels a requested
     * driver pickup job, not the shipment) - consistent with what Dan
     * expected ("not as big of a deal for DPD as they only charge us for
     * what they scan").
     *
     * Never throws - a failed/impossible cancel (most commonly: APC's
     * already manifested it, and their docs are explicit that cancellation
     * only works up to that point) must never stop stock being put back,
     * since the stock movements below are the actual point of this
     * operation. The outcome is returned as a warning string instead, for
     * the controller to pass back to the frontend so staff know to check
     * APC's own portal if it matters (i.e. if it was actually manifested -
     * that's also the point APC starts billing for it).
     */
    private String cancelApcShipmentIfBooked(Order order) {
        if (order.getApcWaybill() == null) return null;
        try {
            apcShippingService.cancelOrder(order);
            return null;
        } catch (Exception e) {
            log.warn("Couldn't cancel APC shipment {} for order {} via their API: {}",
                    order.getApcWaybill(), order.getOrderNumber(), e.getMessage());
            return "Stock was returned, but the APC shipment couldn't be cancelled via their API (" + e.getMessage()
                    + ") - if it's already been manifested, cancel/void it from APC's own portal if you don't want to be billed for it.";
        }
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
