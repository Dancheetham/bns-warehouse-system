package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uk.co.bns.warehouse_api.dto.*;
import uk.co.bns.warehouse_api.entity.*;
import uk.co.bns.warehouse_api.enums.MovementType;
import uk.co.bns.warehouse_api.enums.StockItemStatus;
import uk.co.bns.warehouse_api.enums.TrackingType;
import uk.co.bns.warehouse_api.exception.ValidationException;
import uk.co.bns.warehouse_api.repository.*;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;

/**
 * One-off bulk replacement of current on-hand stock from an OrderWise export -
 * not an ongoing sync, a "go live" seeding operation. Deliberately narrow in
 * what it touches, per explicit decisions made with the business owner:
 *
 *  - Only ever replaces AVAILABLE/QUARANTINED stock (what's genuinely on the
 *    shelves right now) - DESPATCHED history and ALLOCATED (committed to an
 *    open order) items are never touched, system-wide, regardless of whether
 *    their product appears in the file.
 *  - A VariantCode that doesn't match an existing product is skipped and
 *    reported, never auto-created as a bare product.
 *  - The file's Qty-based tracking type (MAC if any row for that SKU carries
 *    an identifier, otherwise NONE) is authoritative and overrides whatever a
 *    product's tracking type currently is.
 *  - SerialNo maps to macAddress, not serialNumber - this system doesn't
 *    currently distinguish the two functionally, and OrderWise never tracked
 *    a separate MAC concept at all.
 *
 * preview() and commit() share the exact same plan-building logic
 * (buildPlan) specifically so the preview can never promise something commit
 * doesn't actually do - the two must never be allowed to drift apart.
 */
@Service
@RequiredArgsConstructor
public class StockImportService {

    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final InventoryRepository inventoryRepository;

    private record ParsedRow(String bin, String sku, String batch, String mac, int qty) {}

    private static class ImportPlan {
        List<String> binsToCreate = new ArrayList<>();
        Map<String, Product> matchedProductsBySku = new LinkedHashMap<>();
        Map<String, List<ParsedRow>> matchedRowsBySku = new LinkedHashMap<>();
        Map<String, TrackingType> newTrackingTypeBySku = new LinkedHashMap<>();
        List<UnmatchedSkuSummary> unmatchedSkus = new ArrayList<>();
        List<TrackingTypeChange> trackingTypeChanges = new ArrayList<>();
        List<String> edgeCaseNotes = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int totalRows = 0;
        int itemsToCreate = 0;
    }

    public StockImportPreview preview(MultipartFile file) {
        ImportPlan plan = buildPlan(file);
        long currentOnHand = stockItemRepository
                .findByStatusIn(List.of(StockItemStatus.AVAILABLE, StockItemStatus.QUARANTINED)).size();

        return new StockImportPreview(
                plan.totalRows,
                plan.binsToCreate,
                plan.matchedProductsBySku.size(),
                plan.unmatchedSkus,
                plan.trackingTypeChanges,
                plan.itemsToCreate,
                (int) currentOnHand,
                plan.edgeCaseNotes,
                plan.errors
        );
    }

    @Transactional
    public StockImportResult commit(MultipartFile file, String performedBy) {
        ImportPlan plan = buildPlan(file);
        if (!plan.errors.isEmpty()) {
            return new StockImportResult(false, 0, 0, 0, plan.unmatchedSkus.size(), plan.errors);
        }

        Map<String, Location> locationsByCode = new HashMap<>();
        int binsCreated = 0;
        for (String code : plan.binsToCreate) {
            Location location = new Location();
            location.setCode(code);
            location = locationRepository.save(location);
            locationsByCode.put(code, location);
            binsCreated++;
        }
        for (Location location : locationRepository.findAll()) {
            locationsByCode.putIfAbsent(location.getCode().toUpperCase(), location);
        }

        List<StockItem> toRemove = stockItemRepository
                .findByStatusIn(List.of(StockItemStatus.AVAILABLE, StockItemStatus.QUARANTINED));
        int itemsRemoved = toRemove.size();
        stockItemRepository.deleteAll(toRemove);

        int itemsCreated = 0;
        LocalDateTime importedAt = LocalDateTime.now();
        String reference = "STOCK-IMPORT-" + importedAt.toLocalDate();

        for (Map.Entry<String, Product> entry : plan.matchedProductsBySku.entrySet()) {
            String sku = entry.getKey();
            Product product = entry.getValue();
            TrackingType newType = plan.newTrackingTypeBySku.get(sku);
            if (newType != null && newType != product.getTrackingType()) {
                product.setTrackingType(newType);
                productRepository.save(product);
            }

            for (ParsedRow row : plan.matchedRowsBySku.get(sku)) {
                Location location = locationsByCode.get(row.bin());
                boolean singleTrackedUnit = row.qty() == 1 && row.mac() != null;
                int unitsToCreate = singleTrackedUnit ? 1 : row.qty();

                for (int i = 0; i < unitsToCreate; i++) {
                    StockItem item = new StockItem();
                    item.setProduct(product);
                    item.setLocation(location);
                    item.setStatus(StockItemStatus.AVAILABLE);
                    item.setBatchCode(row.batch());
                    if (singleTrackedUnit && i == 0) {
                        item.setMacAddress(row.mac());
                    }
                    item = stockItemRepository.save(item);

                    StockMovement movement = new StockMovement();
                    movement.setStockItem(item);
                    movement.setProduct(product);
                    movement.setToLocation(location);
                    movement.setMovementType(MovementType.RECEIPT);
                    movement.setQuantity(1);
                    movement.setReference(reference);
                    movement.setNotes("Bulk stock import from OrderWise export");
                    movement.setCreatedBy(performedBy);
                    stockMovementRepository.save(movement);

                    itemsCreated++;
                }
            }
        }

        recomputeInventory();

        return new StockImportResult(true, binsCreated, itemsRemoved, itemsCreated, plan.unmatchedSkus.size(), List.of());
    }

    private void recomputeInventory() {
        inventoryRepository.deleteAll();
        List<StockItem> physicallyPresent = stockItemRepository.findByStatusIn(
                List.of(StockItemStatus.AVAILABLE, StockItemStatus.QUARANTINED, StockItemStatus.ALLOCATED));

        Map<String, Inventory> byKey = new LinkedHashMap<>();
        for (StockItem item : physicallyPresent) {
            if (item.getLocation() == null) continue;
            String key = item.getProduct().getId() + ":" + item.getLocation().getId();
            Inventory inv = byKey.computeIfAbsent(key, k -> {
                Inventory i = new Inventory();
                i.setProduct(item.getProduct());
                i.setLocation(item.getLocation());
                i.setQuantity(0);
                return i;
            });
            inv.setQuantity(inv.getQuantity() + 1);
        }
        inventoryRepository.saveAll(byKey.values());
    }

    private ImportPlan buildPlan(MultipartFile file) {
        ImportPlan plan = new ImportPlan();

        List<ParsedRow> rows;
        try {
            rows = readRows(file);
        } catch (IOException e) {
            plan.errors.add("Could not read the spreadsheet: " + e.getMessage());
            return plan;
        }
        if (rows.isEmpty()) {
            plan.errors.add("The spreadsheet contained no data rows");
            return plan;
        }
        plan.totalRows = rows.size();

        Map<String, List<ParsedRow>> rowsBySku = new LinkedHashMap<>();
        for (ParsedRow row : rows) {
            if (row.sku() == null || row.sku().isBlank()) continue;
            rowsBySku.computeIfAbsent(row.sku(), k -> new ArrayList<>()).add(row);
        }

        Map<String, Product> productsBySku = new HashMap<>();
        for (Product p : productRepository.findAll()) {
            productsBySku.put(p.getSku().toUpperCase(), p);
        }
        Set<String> existingLocationCodes = new HashSet<>();
        for (Location l : locationRepository.findAll()) {
            existingLocationCodes.add(l.getCode().toUpperCase());
        }

        Set<String> binsToCreate = new LinkedHashSet<>();

        for (Map.Entry<String, List<ParsedRow>> entry : rowsBySku.entrySet()) {
            String sku = entry.getKey();
            List<ParsedRow> skuRows = entry.getValue();
            Product product = productsBySku.get(sku);

            if (product == null) {
                int totalQty = skuRows.stream().mapToInt(ParsedRow::qty).sum();
                plan.unmatchedSkus.add(new UnmatchedSkuSummary(sku, skuRows.size(), totalQty));
                continue;
            }

            plan.matchedProductsBySku.put(sku, product);
            plan.matchedRowsBySku.put(sku, skuRows);

            boolean anyIdentifier = skuRows.stream().anyMatch(r -> r.qty() == 1 && r.mac() != null);
            TrackingType newType = anyIdentifier ? TrackingType.MAC : TrackingType.NONE;
            plan.newTrackingTypeBySku.put(sku, newType);
            if (newType != product.getTrackingType()) {
                plan.trackingTypeChanges.add(new TrackingTypeChange(sku, product.getTrackingType().name(), newType.name()));
            }

            for (ParsedRow row : skuRows) {
                if (!existingLocationCodes.contains(row.bin())) {
                    binsToCreate.add(row.bin());
                }
                boolean singleTrackedUnit = row.qty() == 1 && row.mac() != null;
                if (row.qty() == 1 && row.mac() == null) {
                    plan.edgeCaseNotes.add(sku + " at bin " + row.bin()
                            + ": quantity is 1 but no serial/MAC was recorded - importing as one untracked unit");
                }
                plan.itemsToCreate += singleTrackedUnit ? 1 : row.qty();
            }
        }

        plan.binsToCreate = new ArrayList<>(binsToCreate);
        plan.unmatchedSkus.sort(Comparator.comparing(UnmatchedSkuSummary::sku));
        return plan;
    }

    private static final List<String> KNOWN_HEADERS =
            List.of("binnumber", "variantcode", "batchno", "serialno", "qty");

    private List<ParsedRow> readRows(MultipartFile file) throws IOException {
        List<ParsedRow> rows = new ArrayList<>();
        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) return rows;

            Map<Integer, String> columnMap = new HashMap<>();
            for (Cell cell : headerRow) {
                String header = formatter.formatCellValue(cell).trim().toLowerCase().replace(" ", "");
                if (KNOWN_HEADERS.contains(header)) {
                    columnMap.put(cell.getColumnIndex(), header);
                }
            }
            if (!columnMap.containsValue("variantcode") || !columnMap.containsValue("binnumber")
                    || !columnMap.containsValue("qty")) {
                throw new ValidationException("Expected columns BinNumber, VariantCode and Qty were not found in the spreadsheet");
            }

            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                Map<String, String> rowData = new HashMap<>();
                for (Map.Entry<Integer, String> col : columnMap.entrySet()) {
                    Cell cell = row.getCell(col.getKey());
                    rowData.put(col.getValue(), cell == null ? "" : formatter.formatCellValue(cell).trim());
                }

                String sku = rowData.getOrDefault("variantcode", "").toUpperCase();
                String bin = rowData.getOrDefault("binnumber", "").toUpperCase();
                if (sku.isEmpty() || bin.isEmpty()) continue;

                String batch = blankToNull(rowData.get("batchno"));
                if (batch != null) batch = batch.toUpperCase();
                String mac = blankToNull(rowData.get("serialno"));
                if (mac != null) mac = mac.toUpperCase();

                int qty;
                try {
                    qty = (int) Double.parseDouble(rowData.getOrDefault("qty", "0").replace(",", ""));
                } catch (NumberFormatException e) {
                    qty = 0;
                }
                if (qty <= 0) continue;

                rows.add(new ParsedRow(bin, sku, batch, mac, qty));
            }
        }
        return rows;
    }

    private String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
