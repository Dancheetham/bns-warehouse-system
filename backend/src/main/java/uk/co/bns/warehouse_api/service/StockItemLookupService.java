package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.bns.warehouse_api.dto.MoveItemsResult;
import uk.co.bns.warehouse_api.dto.StockItemSummary;
import uk.co.bns.warehouse_api.entity.Location;
import uk.co.bns.warehouse_api.entity.Product;
import uk.co.bns.warehouse_api.entity.StockItem;
import uk.co.bns.warehouse_api.entity.StockMovement;
import uk.co.bns.warehouse_api.enums.MovementType;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.repository.LocationRepository;
import uk.co.bns.warehouse_api.repository.StockItemRepository;
import uk.co.bns.warehouse_api.repository.StockMovementRepository;

import java.util.*;

/**
 * Backs the Stock Movement screen: find specific physical units by MAC, serial, or
 * batch/carton code (or browse everything for a product), then move exactly those
 * units - no quantity typing, no intermediate confirmation screens. A MAC/serial
 * tracked item can only ever be 1 of itself, so there's nothing to type there;
 * scanning a batch code adds every unit in that carton in one go.
 */
@Service
@RequiredArgsConstructor
public class StockItemLookupService {

    private final StockItemRepository stockItemRepository;
    private final LocationRepository locationRepository;
    private final StockMovementRepository stockMovementRepository;

    public StockItemSummary findByMac(String mac) {
        StockItem item = stockItemRepository.findByMacAddressIgnoreCase(mac)
                .orElseThrow(() -> new NotFoundException("No stock item found for MAC " + mac));
        return toSummary(item);
    }

    public StockItemSummary findBySerial(String serial) {
        StockItem item = stockItemRepository.findBySerialNumberIgnoreCase(serial)
                .orElseThrow(() -> new NotFoundException("No stock item found for serial " + serial));
        return toSummary(item);
    }

    public List<StockItemSummary> findByBatch(String batchCode) {
        List<StockItem> items = stockItemRepository.findByBatchCodeIgnoreCase(batchCode);
        if (items.isEmpty()) {
            throw new NotFoundException("No stock items found for batch/carton " + batchCode);
        }
        return items.stream().map(this::toSummary).toList();
    }

    public List<StockItemSummary> listByProduct(Long productId) {
        List<StockItem> items = stockItemRepository.findByProduct_Id(productId);
        return items.stream()
                .sorted(Comparator
                        .comparing((StockItem i) -> i.getLocation() != null ? i.getLocation().getCode() : "")
                        .thenComparing(i -> Optional.ofNullable(i.getMacAddress()).orElse(Optional.ofNullable(i.getSerialNumber()).orElse(""))))
                .map(this::toSummary)
                .toList();
    }

    public List<uk.co.bns.warehouse_api.dto.BinProductGroup> getBinContents(Long locationId) {
        if (!locationRepository.existsById(locationId)) {
            throw new NotFoundException("Location " + locationId + " not found");
        }
        List<StockItem> items = stockItemRepository.findByLocation_Id(locationId);

        Map<Long, List<StockItem>> byProduct = new LinkedHashMap<>();
        for (StockItem item : items) {
            byProduct.computeIfAbsent(item.getProduct().getId(), k -> new ArrayList<>()).add(item);
        }

        List<uk.co.bns.warehouse_api.dto.BinProductGroup> groups = new ArrayList<>();
        for (List<StockItem> group : byProduct.values()) {
            Product product = group.get(0).getProduct();
            List<uk.co.bns.warehouse_api.dto.StockItemDetail> details = group.stream()
                    .sorted(Comparator.comparing(i -> Optional.ofNullable(i.getMacAddress())
                            .orElse(Optional.ofNullable(i.getSerialNumber()).orElse(""))))
                    .map(this::toDetail)
                    .toList();
            groups.add(new uk.co.bns.warehouse_api.dto.BinProductGroup(
                    product.getId(), product.getSku(), product.getName(), product.getDefaultPassword(), details));
        }
        groups.sort(Comparator.comparing(uk.co.bns.warehouse_api.dto.BinProductGroup::productSku));
        return groups;
    }

    @Transactional
    public MoveItemsResult moveItems(List<Long> stockItemIds, Long toLocationId, String movedBy, String notes) {
        Location destination = locationRepository.findById(toLocationId)
                .orElseThrow(() -> new NotFoundException("Location " + toLocationId + " not found"));

        List<StockItem> items = stockItemRepository.findAllById(stockItemIds);
        Map<Long, StockItem> itemById = new HashMap<>();
        for (StockItem item : items) {
            itemById.put(item.getId(), item);
        }

        int moved = 0;
        List<String> skipped = new ArrayList<>();

        for (Long id : stockItemIds) {
            StockItem item = itemById.get(id);
            if (item == null) {
                skipped.add("Item " + id + " not found");
                continue;
            }
            if (item.getLocation() != null && item.getLocation().getId().equals(destination.getId())) {
                skipped.add(displayName(item) + " is already at " + destination.getCode());
                continue;
            }

            Location from = item.getLocation();
            item.setLocation(destination);
            stockItemRepository.save(item);

            StockMovement movement = new StockMovement();
            movement.setStockItem(item);
            movement.setProduct(item.getProduct());
            movement.setFromLocation(from);
            movement.setToLocation(destination);
            movement.setMovementType(MovementType.MOVE);
            movement.setQuantity(1);
            movement.setNotes(notes);
            movement.setCreatedBy(movedBy);
            stockMovementRepository.save(movement);

            moved++;
        }

        return new MoveItemsResult(moved, skipped.size(), skipped);
    }

    private String displayName(StockItem item) {
        if (item.getMacAddress() != null) return item.getMacAddress();
        if (item.getSerialNumber() != null) return item.getSerialNumber();
        return "Item " + item.getId();
    }

    private StockItemSummary toSummary(StockItem item) {
        Product product = item.getProduct();
        Location location = item.getLocation();
        return new StockItemSummary(
                item.getId(),
                item.getMacAddress(),
                item.getSerialNumber(),
                item.getWifiMacAddress(),
                item.getBatchCode(),
                product.getSku(),
                product.getName(),
                location != null ? location.getId() : null,
                location != null ? location.getCode() : null,
                item.getStatus().name()
        );
    }

    private uk.co.bns.warehouse_api.dto.StockItemDetail toDetail(StockItem item) {
        Location location = item.getLocation();
        return new uk.co.bns.warehouse_api.dto.StockItemDetail(
                item.getId(),
                item.getMacAddress(),
                item.getSerialNumber(),
                item.getWifiMacAddress(),
                item.getBatchCode(),
                item.getStatus().name(),
                location != null ? location.getCode() : null
        );
    }
}
