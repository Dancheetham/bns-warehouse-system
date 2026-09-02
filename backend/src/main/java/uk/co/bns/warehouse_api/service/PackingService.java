package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.bns.warehouse_api.dto.*;
import uk.co.bns.warehouse_api.entity.Carton;
import uk.co.bns.warehouse_api.entity.CartonLine;
import uk.co.bns.warehouse_api.entity.Order;
import uk.co.bns.warehouse_api.entity.OrderLine;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.exception.ValidationException;
import uk.co.bns.warehouse_api.repository.CartonLineRepository;
import uk.co.bns.warehouse_api.repository.CartonRepository;
import uk.co.bns.warehouse_api.repository.OrderRepository;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Packing here means splitting an order line's picked quantity across cartons, not
 * assigning individual serial numbers to boxes - mirrors the existing "Split Line"
 * (into two: N and the remainder) and "Split Line by Quantity" (into equal-sized
 * boxes of N, with a shorter final one if it doesn't divide evenly) behaviour.
 *
 * Every picked order line starts life as a single unassigned CartonLine covering
 * its full quantityPicked. Splitting divides a slice into more slices; assigning
 * puts a slice into a Carton. The invariant that always holds: a line's CartonLines
 * sum to its quantityPicked.
 */
@Service
@RequiredArgsConstructor
public class PackingService {

    private final OrderRepository orderRepository;
    private final CartonRepository cartonRepository;
    private final CartonLineRepository cartonLineRepository;

    public PackingView getPackingView(Long orderId) {
        Order order = findOrder(orderId);
        ensureInitialised(order);
        return toView(order);
    }

    @Transactional
    public PackingView addCarton(Long orderId) {
        Order order = findOrder(orderId);
        cartonRepository.save(newCarton(order));
        return toView(order);
    }

    @Transactional
    public PackingView deleteCarton(Long orderId, Long cartonId) {
        Order order = findOrder(orderId);
        Carton carton = findCarton(order, cartonId);
        if (!cartonLineRepository.findByCarton_Id(cartonId).isEmpty()) {
            throw new ValidationException("Remove the lines from this carton before deleting it");
        }
        cartonRepository.delete(carton);
        return toView(order);
    }

    @Transactional
    public PackingView setCartonWeight(Long orderId, Long cartonId, BigDecimal weightKg) {
        Order order = findOrder(orderId);
        Carton carton = findCarton(order, cartonId);
        carton.setWeightKg(weightKg);
        cartonRepository.save(carton);
        return toView(order);
    }

    /**
     * Splits a slice into two: one of `amount`, one of whatever's left over. Only
     * works on an unassigned slice - unassign it first if it's already in a carton.
     */
    @Transactional
    public PackingView splitLine(Long orderId, SplitLineRequest request) {
        Order order = findOrder(orderId);
        CartonLine line = findUnassignedLine(order, request.cartonLineId());

        if (request.amount() >= line.getQuantity()) {
            throw new ValidationException("Split amount must be less than the line's quantity (" + line.getQuantity() + ")");
        }

        int remainder = line.getQuantity() - request.amount();
        line.setQuantity(request.amount());
        cartonLineRepository.save(line);

        CartonLine second = new CartonLine();
        second.setOrderLine(line.getOrderLine());
        second.setQuantity(remainder);
        cartonLineRepository.save(second);

        return toView(order);
    }

    /**
     * Splits a slice into equal-sized boxes of `boxSize`, with a shorter final line
     * if it doesn't divide evenly - e.g. 10 split by quantity 3 -> 3, 3, 3, 1.
     */
    @Transactional
    public PackingView splitLineByQuantity(Long orderId, SplitLineByQuantityRequest request) {
        Order order = findOrder(orderId);
        CartonLine line = findUnassignedLine(order, request.cartonLineId());

        if (request.boxSize() > line.getQuantity()) {
            throw new ValidationException("Box size can't be more than the line's quantity (" + line.getQuantity() + ")");
        }

        int total = line.getQuantity();
        int boxSize = request.boxSize();
        int fullBoxes = total / boxSize;
        int remainder = total % boxSize;

        line.setQuantity(boxSize);
        cartonLineRepository.save(line);

        for (int i = 1; i < fullBoxes; i++) {
            CartonLine extra = new CartonLine();
            extra.setOrderLine(line.getOrderLine());
            extra.setQuantity(boxSize);
            cartonLineRepository.save(extra);
        }
        if (remainder > 0) {
            CartonLine extra = new CartonLine();
            extra.setOrderLine(line.getOrderLine());
            extra.setQuantity(remainder);
            cartonLineRepository.save(extra);
        }

        return toView(order);
    }

    @Transactional
    public PackingView assignLine(Long orderId, AssignCartonLineRequest request) {
        Order order = findOrder(orderId);
        CartonLine line = cartonLineRepository.findById(request.cartonLineId())
                .orElseThrow(() -> new NotFoundException("Carton line " + request.cartonLineId() + " not found"));
        if (!line.getOrderLine().getOrder().getId().equals(orderId)) {
            throw new ValidationException("That line doesn't belong to this order");
        }

        if (request.cartonId() == null) {
            line.setCarton(null);
        } else {
            line.setCarton(findCarton(order, request.cartonId()));
        }
        cartonLineRepository.save(line);
        return toView(order);
    }

    /**
     * Used by despatch confirmation so nothing is blocked on packing being finished -
     * any slice still unassigned when despatch is confirmed lands in one final
     * catch-all carton rather than being left off a label.
     */
    @Transactional
    void autoAssignRemaining(Order order) {
        ensureInitialised(order);
        List<CartonLine> unassigned = cartonLineRepository.findByOrderLine_Order_Id(order.getId())
                .stream().filter(l -> l.getCarton() == null).toList();
        if (unassigned.isEmpty()) return;

        Carton carton = cartonRepository.save(newCarton(order));
        for (CartonLine line : unassigned) {
            line.setCarton(carton);
            cartonLineRepository.save(line);
        }
    }

    /**
     * Guarantees every picked order line has at least one CartonLine covering its
     * full quantityPicked before it's ever shown or split - lazily created the
     * first time packing is opened for an order.
     */
    private void ensureInitialised(Order order) {
        for (OrderLine line : order.getLines()) {
            if (line.getQuantityPicked() <= 0) continue;
            if (cartonLineRepository.existsByOrderLine_Id(line.getId())) continue;

            CartonLine initial = new CartonLine();
            initial.setOrderLine(line);
            initial.setQuantity(line.getQuantityPicked());
            cartonLineRepository.save(initial);
        }
    }

    private Carton newCarton(Order order) {
        int nextNumber = cartonRepository.countByOrder_Id(order.getId()) + 1;
        Carton carton = new Carton();
        carton.setOrder(order);
        carton.setCartonNumber(nextNumber);
        return carton;
    }

    private PackingView toView(Order order) {
        List<CartonLine> allLines = cartonLineRepository.findByOrderLine_Order_Id(order.getId());
        List<Carton> cartons = cartonRepository.findByOrder_IdOrderByCartonNumberAsc(order.getId());

        List<PackLineView> unassigned = allLines.stream()
                .filter(l -> l.getCarton() == null)
                .map(this::toLineView)
                .toList();

        List<CartonView> cartonViews = cartons.stream().map(carton -> {
            List<CartonLine> lines = allLines.stream()
                    .filter(l -> l.getCarton() != null && l.getCarton().getId().equals(carton.getId()))
                    .toList();
            BigDecimal computed = lines.stream()
                    .filter(l -> l.getOrderLine().getProduct().getWeightKg() != null)
                    .map(l -> l.getOrderLine().getProduct().getWeightKg().multiply(BigDecimal.valueOf(l.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new CartonView(
                    carton.getId(), carton.getCartonNumber(), carton.getWeightKg(), computed,
                    carton.getTrackingNumber(), lines.stream().map(this::toLineView).toList());
        }).sorted(Comparator.comparingInt(CartonView::cartonNumber)).toList();

        boolean allAssigned = unassigned.isEmpty() && !allLines.isEmpty();

        return new PackingView(order.getId(), order.getOrderNumber(), order.getCustomerName(),
                unassigned, cartonViews, allAssigned);
    }

    private PackLineView toLineView(CartonLine line) {
        return new PackLineView(
                line.getId(), line.getOrderLine().getId(),
                line.getOrderLine().getProduct().getSku(), line.getOrderLine().getProduct().getName(),
                line.getQuantity(), line.getCarton() != null ? line.getCarton().getId() : null);
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

    private CartonLine findUnassignedLine(Order order, Long cartonLineId) {
        CartonLine line = cartonLineRepository.findById(cartonLineId)
                .orElseThrow(() -> new NotFoundException("Carton line " + cartonLineId + " not found"));
        if (!line.getOrderLine().getOrder().getId().equals(order.getId())) {
            throw new ValidationException("That line doesn't belong to this order");
        }
        if (line.getCarton() != null) {
            throw new ValidationException("Unassign this line from its carton before splitting it");
        }
        return line;
    }
}
