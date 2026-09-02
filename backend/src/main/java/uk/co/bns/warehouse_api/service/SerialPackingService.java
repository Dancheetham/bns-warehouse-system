package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.bns.warehouse_api.dto.*;
import uk.co.bns.warehouse_api.entity.Carton;
import uk.co.bns.warehouse_api.entity.Order;
import uk.co.bns.warehouse_api.entity.StockItem;
import uk.co.bns.warehouse_api.enums.StockItemStatus;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.exception.ValidationException;
import uk.co.bns.warehouse_api.repository.CartonRepository;
import uk.co.bns.warehouse_api.repository.OrderRepository;
import uk.co.bns.warehouse_api.repository.StockItemRepository;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * "Serial Packing" - the alternative to quantity-split packing (PackingService),
 * where each individual picked unit is assigned to a physical carton by scan/serial
 * rather than by splitting a line's quantity. Which mode is active is a Settings
 * toggle (packing_mode); this service only does anything when it's SERIAL.
 */
@Service
@RequiredArgsConstructor
public class SerialPackingService {

    private final OrderRepository orderRepository;
    private final CartonRepository cartonRepository;
    private final StockItemRepository stockItemRepository;

    public SerialPackingView getPackingView(Long orderId) {
        Order order = findOrder(orderId);
        return toView(order);
    }

    @Transactional
    public SerialPackingView addCarton(Long orderId) {
        Order order = findOrder(orderId);
        cartonRepository.save(newCarton(order));
        return toView(order);
    }

    @Transactional
    public SerialPackingView deleteCarton(Long orderId, Long cartonId) {
        Order order = findOrder(orderId);
        Carton carton = findCarton(order, cartonId);
        if (!stockItemRepository.findByCarton_Id(cartonId).isEmpty()) {
            throw new ValidationException("Remove the items from this carton before deleting it");
        }
        cartonRepository.delete(carton);
        return toView(order);
    }

    @Transactional
    public SerialPackingView setCartonWeight(Long orderId, Long cartonId, BigDecimal weightKg) {
        Order order = findOrder(orderId);
        Carton carton = findCarton(order, cartonId);
        carton.setWeightKg(weightKg);
        cartonRepository.save(carton);
        return toView(order);
    }

    @Transactional
    public SerialPackingView assignItem(Long orderId, AssignCartonItemRequest request) {
        Order order = findOrder(orderId);
        StockItem item = stockItemRepository.findById(request.stockItemId())
                .orElseThrow(() -> new NotFoundException("Stock item " + request.stockItemId() + " not found"));
        if (item.getOrderLine() == null || !item.getOrderLine().getOrder().getId().equals(orderId)) {
            throw new ValidationException("That item isn't picked against this order");
        }

        if (request.cartonId() == null) {
            item.setCarton(null);
        } else {
            item.setCarton(findCarton(order, request.cartonId()));
        }
        stockItemRepository.save(item);
        return toView(order);
    }

    /**
     * Used by despatch confirmation so nothing is blocked on packing being finished -
     * any picked item still unassigned when despatch is confirmed lands in one final
     * catch-all carton rather than being left off a label.
     */
    @Transactional
    void autoAssignRemaining(Order order) {
        List<StockItem> unassigned = stockItemRepository.findByOrderLine_Order_IdAndStatus(order.getId(), StockItemStatus.ALLOCATED)
                .stream().filter(i -> i.getCarton() == null).toList();
        if (unassigned.isEmpty()) return;

        Carton carton = cartonRepository.save(newCarton(order));
        for (StockItem item : unassigned) {
            item.setCarton(carton);
            stockItemRepository.save(item);
        }
    }

    private Carton newCarton(Order order) {
        int nextNumber = cartonRepository.countByOrder_Id(order.getId()) + 1;
        Carton carton = new Carton();
        carton.setOrder(order);
        carton.setCartonNumber(nextNumber);
        return carton;
    }

    private SerialPackingView toView(Order order) {
        List<StockItem> pickedItems = stockItemRepository
                .findByOrderLine_Order_IdAndStatus(order.getId(), StockItemStatus.ALLOCATED);

        List<Carton> cartons = cartonRepository.findByOrder_IdOrderByCartonNumberAsc(order.getId());

        List<PackedItemView> unassigned = pickedItems.stream()
                .filter(i -> i.getCarton() == null)
                .map(this::toItemView)
                .toList();

        List<SerialCartonView> cartonViews = cartons.stream().map(carton -> {
            List<StockItem> items = pickedItems.stream()
                    .filter(i -> i.getCarton() != null && i.getCarton().getId().equals(carton.getId()))
                    .toList();
            BigDecimal computed = items.stream()
                    .map(i -> i.getProduct().getWeightKg())
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new SerialCartonView(
                    carton.getId(), carton.getCartonNumber(), carton.getWeightKg(), computed,
                    carton.getTrackingNumber(),
                    items.stream().map(this::toItemView).toList());
        }).sorted(Comparator.comparingInt(SerialCartonView::cartonNumber)).toList();

        boolean allAssigned = unassigned.isEmpty() && !pickedItems.isEmpty();

        return new SerialPackingView(order.getId(), order.getOrderNumber(), order.getCustomerName(),
                unassigned, cartonViews, allAssigned);
    }

    private PackedItemView toItemView(StockItem item) {
        String identifier = item.getMacAddress() != null ? item.getMacAddress()
                : item.getSerialNumber() != null ? item.getSerialNumber()
                : item.getBatchCode() != null ? item.getBatchCode()
                : "(unit)";
        return new PackedItemView(item.getId(), item.getProduct().getSku(), item.getProduct().getName(),
                identifier, item.getCarton() != null ? item.getCarton().getId() : null);
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order " + orderId + " not found"));
    }

    private Carton findCarton(Order order, Long cartonId) {
        Carton carton = cartonRepository.findById(cartonId)
                .orElseThrow(() -> new NotFoundException("Carton " + cartonId + " not found"));
        if (!carton.getOrder().getId().equals(order.getId())) {
            throw new ValidationException("That carton doesn't belong to this order");
        }
        return carton;
    }
}
