package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.bns.warehouse_api.dto.AcknowledgementResult;
import uk.co.bns.warehouse_api.dto.DespatchConfirmationResult;
import uk.co.bns.warehouse_api.dto.DpdShipmentResult;
import uk.co.bns.warehouse_api.dto.OrderPickSummary;
import uk.co.bns.warehouse_api.entity.Carton;
import uk.co.bns.warehouse_api.entity.Order;
import uk.co.bns.warehouse_api.entity.OrderLine;
import uk.co.bns.warehouse_api.entity.StockItem;
import uk.co.bns.warehouse_api.entity.StockMovement;
import uk.co.bns.warehouse_api.enums.MovementType;
import uk.co.bns.warehouse_api.enums.OrderStatus;
import uk.co.bns.warehouse_api.enums.PickingStatus;
import uk.co.bns.warehouse_api.enums.StockItemStatus;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.exception.ValidationException;
import uk.co.bns.warehouse_api.repository.CartonRepository;
import uk.co.bns.warehouse_api.repository.OrderRepository;
import uk.co.bns.warehouse_api.repository.StockItemRepository;
import uk.co.bns.warehouse_api.repository.StockMovementRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks up once a handheld pick is COMPLETE or PARTIAL, after packing (PackingService)
 * has assigned picked items into cartons. Confirming despatch finally consumes the
 * picked StockItems (ALLOCATED -> DESPATCHED) and closes out the order - the dummy
 * shipping labels themselves are generated separately by ShippingLabelService.
 */
@Service
@RequiredArgsConstructor
public class DespatchService {

    private static final Logger log = LoggerFactory.getLogger(DespatchService.class);

    private final OrderRepository orderRepository;
    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final CartonRepository cartonRepository;
    private final InventoryService inventoryService;
    private final PackingService packingService;
    private final SerialPackingService serialPackingService;
    private final SettingsService settingsService;
    private final ShopifyFulfillmentService shopifyFulfillmentService;
    private final DespatchConfirmationService despatchConfirmationService;
    private final DpdShippingService dpdShippingService;

    public static final String PACKING_MODE_KEY = "packing_mode";
    public static final String PACKING_MODE_SPLIT = "SPLIT";
    public static final String PACKING_MODE_SERIAL = "SERIAL";

    public List<OrderPickSummary> readyToPack() {
        List<Order> orders = orderRepository.findByStatusAndPickingStatusInOrderByOrderDateAsc(
                OrderStatus.AWAITING_DESPATCH, List.of(PickingStatus.COMPLETE, PickingStatus.PARTIAL));
        return orders.stream()
                .map(o -> new OrderPickSummary(o.getId(), o.getOrderNumber(), o.getCustomerName(),
                        o.getOrderDate(), o.getLines().size(), o.getPickingStatus(), o.getPickedBy()))
                .toList();
    }

    @Transactional
    public DespatchConfirmationResult confirmDespatch(Long orderId, String performedByName) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order " + orderId + " not found"));

        if (order.getPickingStatus() != PickingStatus.COMPLETE && order.getPickingStatus() != PickingStatus.PARTIAL) {
            throw new ValidationException("This order hasn't finished picking yet");
        }

        // Checked first, before anything below touches stock or the order's
        // status - if the service saved on the order isn't actually bookable
        // right now, despatch must not proceed at all (stock stays put, the
        // order stays exactly as it was), not "despatch anyway and report the
        // booking failure afterwards" (that's what bookDpdShipment() below
        // still does for other, more transient failures - this is the one
        // case explicitly asked to be a hard stop instead). Skipped entirely
        // once a shipment's already booked for this order - nothing to
        // re-validate at that point.
        if (order.getDpdShipmentId() == null && !settingsService.get("dpd_api_key", "").isBlank()) {
            dpdShippingService.assertOrderServiceAvailable(order);
        }

        // Anything still unpacked at this point (picker never opened Packing, or
        // left a few units unassigned) gets swept into one final carton so nothing
        // picked is ever left off a label - whichever packing mode is active.
        String mode = settingsService.get(PACKING_MODE_KEY, PACKING_MODE_SPLIT);
        if (PACKING_MODE_SERIAL.equals(mode)) {
            serialPackingService.autoAssignRemaining(order);
        } else {
            packingService.autoAssignRemaining(order);
        }

        List<StockItem> pickedItems = stockItemRepository.findByOrderLine_Order_Id(orderId);
        List<StockItem> despatchedThisTime = new ArrayList<>();
        for (StockItem item : pickedItems) {
            if (item.getStatus() != StockItemStatus.ALLOCATED) continue;

            if (item.getLocation() != null) {
                inventoryService.adjustInventory(item.getProduct(), item.getLocation(), -1);
            }

            StockMovement movement = new StockMovement();
            movement.setStockItem(item);
            movement.setProduct(item.getProduct());
            movement.setFromLocation(item.getLocation());
            movement.setMovementType(MovementType.DESPATCH);
            movement.setQuantity(1);
            movement.setReference("ORDER-" + order.getOrderNumber());
            movement.setCreatedBy(performedByName);
            stockMovementRepository.save(movement);

            item.setStatus(StockItemStatus.DESPATCHED);
            item.setLocation(null);
            stockItemRepository.save(item);
            despatchedThisTime.add(item);
        }

        boolean anyShort = order.getLines().stream()
                .anyMatch(l -> l.getQuantityPicked() < l.getQuantityOrdered());

        for (OrderLine line : order.getLines()) {
            line.setQuantityDespatched(line.getQuantityPicked());
        }

        // A fully-despatched order with a Company (credit account) goes to
        // Generate Invoices (INVOICE_PENDING) rather than straight to
        // COMPLETED - it's only really "done" once it's been invoiced too.
        // An order with no Company (already paid at checkout, e.g. Shopify)
        // has nothing left to invoice after the fact, so it goes straight to
        // COMPLETED exactly as before - see InvoiceService/OrderStatus.
        OrderStatus newStatus;
        if (anyShort) {
            newStatus = OrderStatus.PARTIALLY_DESPATCHED;
        } else if (order.getCompany() != null) {
            newStatus = OrderStatus.INVOICE_PENDING;
        } else {
            newStatus = OrderStatus.COMPLETED;
        }
        order.setStatus(newStatus);
        order = orderRepository.save(order);

        // All three of these are best-effort, deliberately after the order is
        // already saved - none of them should ever be able to block the actual
        // despatch, which is the part that matters (stock genuinely leaving the
        // building). Booking DPD here - at the exact point staff would previously
        // have printed a dummy placeholder label - replaces that with a real
        // shipment and a real tracking number, which is then preferred over the
        // old manually-typed carton tracking number for the Shopify push.
        String dpdStatus = bookDpdShipment(order);

        List<Carton> cartons = cartonRepository.findByOrder_IdOrderByCartonNumberAsc(orderId);
        String manualTrackingNumber = cartons.stream()
                .map(Carton::getTrackingNumber)
                .filter(t -> t != null && !t.isBlank())
                .findFirst()
                .orElse(null);
        String trackingNumber = order.getDpdConsignmentNumber() != null ? order.getDpdConsignmentNumber() : manualTrackingNumber;
        String shopifyStatus = shopifyFulfillmentService.pushFulfillment(order, trackingNumber);
        AcknowledgementResult despatchEmail = despatchConfirmationService.sendDespatchConfirmation(order, despatchedThisTime, performedByName);

        return new DespatchConfirmationResult(order, despatchEmail, shopifyStatus, dpdStatus);
    }

    /**
     * Only attempts a booking when DPD is actually configured (an API key is
     * set) - orders/environments not using DPD yet get a silent null rather
     * than a confusing "DPD not booked" message on every single despatch.
     * Already-booked orders (re-confirming, or booked manually beforehand via
     * the order screen) are left alone rather than booking a second shipment.
     */
    private String bookDpdShipment(Order order) {
        if (order.getDpdShipmentId() != null) {
            return "DPD shipment already booked (consignment " + order.getDpdConsignmentNumber() + ")";
        }
        if (settingsService.get("dpd_api_key", "").isBlank()) {
            return null;
        }
        try {
            DpdShipmentResult result = dpdShippingService.createShipment(order);
            return "DPD shipment booked - consignment " + result.consignmentNumber();
        } catch (Exception e) {
            log.warn("DPD shipment booking failed for order {} at despatch confirmation: {}", order.getOrderNumber(), e.getMessage());
            return "DPD shipment NOT booked: " + e.getMessage() + " - book it manually from the order screen once fixed";
        }
    }
}
