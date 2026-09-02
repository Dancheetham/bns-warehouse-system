package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uk.co.bns.warehouse_api.dto.ImportResult;
import uk.co.bns.warehouse_api.dto.ImportRowResult;
import uk.co.bns.warehouse_api.entity.*;
import uk.co.bns.warehouse_api.enums.POStatus;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.exception.ValidationException;
import uk.co.bns.warehouse_api.repository.ExpectedCartonRepository;
import uk.co.bns.warehouse_api.repository.ExpectedStockItemRepository;
import uk.co.bns.warehouse_api.repository.PurchaseOrderRepository;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Imports the supplier shipment spreadsheet for a Purchase Order.
 *
 * Expected columns (header row required, case-insensitive, order flexible):
 *   SKU | MAC | SERIAL | BATCH | WIFI_MAC (optional)
 *
 * Rules (matching the existing BNS process):
 *  - The quantity of each SKU in the spreadsheet must exactly match the PO line
 *    quantity, or the whole import is rejected. Nothing is partially imported.
 *  - One import per PO. Re-importing after stock has started being received is rejected.
 */
@Service
@RequiredArgsConstructor
public class SpreadsheetImportService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ExpectedCartonRepository expectedCartonRepository;
    private final ExpectedStockItemRepository expectedStockItemRepository;

    private static final List<String> KNOWN_HEADERS = List.of("sku", "mac", "serial", "batch", "wifi_mac");

    @Transactional
    public ImportResult importShipment(Long purchaseOrderId, MultipartFile file) {
        PurchaseOrder po = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new NotFoundException("Purchase order " + purchaseOrderId + " not found"));

        if (po.getStatus() == POStatus.PART_RECEIVED || po.getStatus() == POStatus.RECEIVED) {
            throw new ValidationException("This purchase order has already started receiving stock - re-import is not allowed");
        }

        List<Map<String, String>> rows;
        try {
            rows = readRows(file);
        } catch (IOException e) {
            throw new ValidationException("Could not read the spreadsheet: " + e.getMessage());
        }

        if (rows.isEmpty()) {
            throw new ValidationException("The spreadsheet contained no data rows");
        }

        // 1. Group spreadsheet rows by SKU
        // All identifiers (SKU, MAC, SERIAL, BATCH, WIFI_MAC) are normalised to uppercase
        // on the way in - this keeps stock data consistent regardless of how a supplier
        // formats their spreadsheet. Passwords are a separate, unrelated field and are
        // never touched by this normalisation.
        Map<String, List<Map<String, String>>> rowsBySku = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            uppercaseRow(row);
            String sku = row.getOrDefault("sku", "").trim();
            if (sku.isEmpty()) {
                throw new ValidationException("Every row must have a SKU");
            }
            rowsBySku.computeIfAbsent(sku, k -> new ArrayList<>()).add(row);
        }

        // 2. Validate every PO line quantity matches the spreadsheet exactly
        List<ImportRowResult> validation = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        Map<String, PurchaseOrderLine> lineBySku = new HashMap<>();
        for (PurchaseOrderLine line : po.getLines()) {
            lineBySku.put(line.getProduct().getSku(), line);
        }

        for (PurchaseOrderLine line : po.getLines()) {
            String sku = line.getProduct().getSku();
            int expected = line.getQuantityOrdered();
            int actual = rowsBySku.getOrDefault(sku, List.of()).size();
            boolean ok = expected == actual;
            validation.add(new ImportRowResult(sku, expected, actual, ok));
            if (!ok) {
                errors.add("SKU " + sku + ": PO expects " + expected + " but spreadsheet has " + actual);
            }
        }

        for (String sku : rowsBySku.keySet()) {
            if (!lineBySku.containsKey(sku)) {
                errors.add("SKU " + sku + " appears in the spreadsheet but is not on this purchase order");
            }
        }

        if (!errors.isEmpty()) {
            return new ImportResult(false, 0, 0, validation, errors);
        }

        // 3. All good - create ExpectedCarton + ExpectedStockItem records, grouped by batch code
        int cartonsCreated = 0;
        int itemsCreated = 0;

        for (PurchaseOrderLine line : po.getLines()) {
            String sku = line.getProduct().getSku();
            List<Map<String, String>> skuRows = rowsBySku.getOrDefault(sku, List.of());

            Map<String, List<Map<String, String>>> rowsByBatch = new LinkedHashMap<>();
            for (Map<String, String> row : skuRows) {
                String batch = row.getOrDefault("batch", "").trim();
                if (batch.isEmpty()) {
                    // quantity-only / non-batched product - treat each row as its own "carton"
                    batch = "NOBATCH-" + UUID.randomUUID();
                }
                rowsByBatch.computeIfAbsent(batch, k -> new ArrayList<>()).add(row);
            }

            for (Map.Entry<String, List<Map<String, String>>> entry : rowsByBatch.entrySet()) {
                if (expectedCartonRepository.findByBatchCodeIgnoreCase(entry.getKey()).isPresent()) {
                    throw new ValidationException("Batch/carton code " + entry.getKey() + " has already been imported elsewhere");
                }

                ExpectedCarton carton = new ExpectedCarton();
                carton.setPurchaseOrderLine(line);
                carton.setBatchCode(entry.getKey());
                expectedCartonRepository.save(carton);
                cartonsCreated++;

                for (Map<String, String> row : entry.getValue()) {
                    ExpectedStockItem item = new ExpectedStockItem();
                    item.setExpectedCarton(carton);
                    item.setMacAddress(blankToNull(row.get("mac")));
                    item.setSerialNumber(blankToNull(row.get("serial")));
                    item.setWifiMacAddress(blankToNull(row.get("wifi_mac")));
                    expectedStockItemRepository.save(item);
                    itemsCreated++;
                }
            }
        }

        po.setStatus(POStatus.AWAITING_STOCK);
        purchaseOrderRepository.save(po);

        return new ImportResult(true, cartonsCreated, itemsCreated, validation, List.of());
    }

    private String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void uppercaseRow(Map<String, String> row) {
        for (String key : List.of("sku", "mac", "serial", "batch", "wifi_mac")) {
            String value = row.get(key);
            if (value != null) {
                row.put(key, value.trim().toUpperCase());
            }
        }
    }

    private List<Map<String, String>> readRows(MultipartFile file) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                return rows;
            }

            Map<Integer, String> columnMap = new HashMap<>();
            for (Cell cell : headerRow) {
                String header = formatter.formatCellValue(cell).trim().toLowerCase().replace(" ", "_");
                if (KNOWN_HEADERS.contains(header)) {
                    columnMap.put(cell.getColumnIndex(), header);
                }
            }

            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                Map<String, String> rowData = new HashMap<>();
                boolean anyValue = false;
                for (Map.Entry<Integer, String> col : columnMap.entrySet()) {
                    Cell cell = row.getCell(col.getKey());
                    String value = cell == null ? "" : formatter.formatCellValue(cell).trim();
                    if (!value.isEmpty()) anyValue = true;
                    rowData.put(col.getValue(), value);
                }
                if (anyValue) {
                    rows.add(rowData);
                }
            }
        }
        return rows;
    }
}
