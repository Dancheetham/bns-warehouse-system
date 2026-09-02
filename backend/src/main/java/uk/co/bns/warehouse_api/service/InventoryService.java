package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.bns.warehouse_api.dto.LocationStockSummary;
import uk.co.bns.warehouse_api.dto.MoveStockRequest;
import uk.co.bns.warehouse_api.entity.*;
import uk.co.bns.warehouse_api.enums.MovementType;
import uk.co.bns.warehouse_api.enums.StockItemStatus;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.exception.ValidationException;
import uk.co.bns.warehouse_api.repository.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Every StockItem (serialised or not - see GoodsInService, every unit gets its own row
 * regardless of TrackingType) carries a status and a location, so the per-location
 * breakdown and stock movement logic below works the same way for all product types.
 */
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final StockItemRepository stockItemRepository;
    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;

    public List<LocationStockSummary> getStockSummary(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new NotFoundException("Product " + productId + " not found");
        }

        List<StockItem> items = stockItemRepository.findByProduct_Id(productId);

        Map<Long, Location> locationById = new LinkedHashMap<>();
        Map<Long, int[]> counts = new LinkedHashMap<>(); // [available, quarantined, allocated, despatched, returned]

        for (StockItem item : items) {
            Location loc = item.getLocation();
            if (loc == null) continue; // e.g. despatched and no longer in the warehouse
            locationById.putIfAbsent(loc.getId(), loc);
            int[] c = counts.computeIfAbsent(loc.getId(), k -> new int[5]);
            switch (item.getStatus()) {
                case AVAILABLE -> c[0]++;
                case QUARANTINED -> c[1]++;
                case ALLOCATED -> c[2]++;
                case DESPATCHED -> c[3]++;
                case RETURNED -> c[4]++;
            }
        }

        List<LocationStockSummary> summary = new ArrayList<>();
        for (Map.Entry<Long, Location> entry : locationById.entrySet()) {
            int[] c = counts.get(entry.getKey());
            int total = c[0] + c[1] + c[2] + c[3] + c[4];
            summary.add(new LocationStockSummary(
                    entry.getKey(),
                    entry.getValue().getCode(),
                    entry.getValue().getDescription(),
                    c[0], c[1], c[2], c[3], c[4], total
            ));
        }
        summary.sort(Comparator.comparing(LocationStockSummary::locationCode));
        return summary;
    }

    @Transactional
    public void moveStock(Long productId, MoveStockRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product " + productId + " not found"));
        Location from = locationRepository.findById(request.fromLocationId())
                .orElseThrow(() -> new NotFoundException("Location " + request.fromLocationId() + " not found"));
        Location to = locationRepository.findById(request.toLocationId())
                .orElseThrow(() -> new NotFoundException("Location " + request.toLocationId() + " not found"));

        if (from.getId().equals(to.getId())) {
            throw new ValidationException("From and to location must be different");
        }

        List<StockItem> available = stockItemRepository
                .findByProduct_IdAndLocation_IdAndStatusOrderByIdAsc(productId, from.getId(), StockItemStatus.AVAILABLE);

        if (available.size() < request.quantity()) {
            throw new ValidationException(
                    "Only " + available.size() + " available at " + from.getCode()
                            + " - cannot move " + request.quantity());
        }

        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < request.quantity(); i++) {
            StockItem item = available.get(i);
            item.setLocation(to);
            stockItemRepository.save(item);

            StockMovement movement = new StockMovement();
            movement.setStockItem(item);
            movement.setProduct(product);
            movement.setFromLocation(from);
            movement.setToLocation(to);
            movement.setMovementType(MovementType.MOVE);
            movement.setQuantity(1);
            movement.setNotes(request.notes());
            movement.setCreatedBy(request.movedBy());
            stockMovementRepository.save(movement);
        }

        adjustInventory(product, from, -request.quantity());
        adjustInventory(product, to, request.quantity());
    }

    public void adjustInventory(Product product, Location location, int delta) {
        Inventory inventory = inventoryRepository
                .findByProduct_IdAndLocation_Id(product.getId(), location.getId())
                .orElseGet(() -> {
                    Inventory inv = new Inventory();
                    inv.setProduct(product);
                    inv.setLocation(location);
                    inv.setQuantity(0);
                    return inv;
                });
        inventory.setQuantity(Math.max(0, inventory.getQuantity() + delta));
        inventoryRepository.save(inventory);
    }
}
