package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.bns.warehouse_api.dto.AcknowledgementResult;
import uk.co.bns.warehouse_api.dto.DespatchConfirmationResult;
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
    public DespatchConfirmationResult confirmDespatch(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order " + orderId + " not found"));

        if (order.getPickingStatus() != PickingStatus.COMPLETE && order.getPickingStatus() != PickingStatus.PARTIAL) {
            throw new ValidationException("This order hasn't finished picking yet");
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

        order.setStatus(anyShort ? OrderStatus.PARTIALLY_DESPATCHED : OrderStatus.COMPLETED);
        order = orderRepository.save(order);

        // Both best-effort, deliberately after the order is already saved -
        // neither should ever be able to block the actual despatch, which is
        // the part that matters (stock genuinely leaving the building).
        List<Carton> cartons = cartonRepository.findByOrder_IdOrderByCartonNumberAsc(orderId);
        String dummyTrackingNumber = cartons.stream()
                .map(Carton::getTrackingNumber)
                .filter(t -> t != null && !t.isBlank())
                .findFirst()
                .orElse(null);
        String shopifyStatus = shopifyFulfillmentService.pushFulfillment(order, dummyTrackingNumber);
        AcknowledgementResult despatchEmail = despatchConfirmationService.sendDespatchConfirmation(order, despatchedThisTime);

        return new DespatchConfirmationResult(order, despatchEmail, shopifyStatus);
    }
}
