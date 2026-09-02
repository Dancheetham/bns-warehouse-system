package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import uk.co.bns.warehouse_api.entity.*;
import uk.co.bns.warehouse_api.enums.OrderStatus;
import uk.co.bns.warehouse_api.enums.StockItemStatus;
import uk.co.bns.warehouse_api.repository.OrderRepository;
import uk.co.bns.warehouse_api.repository.ProductRepository;
import uk.co.bns.warehouse_api.repository.StockItemRepository;
import uk.co.bns.warehouse_api.repository.StockMovementRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * All reports are built fresh from live data at request time (same source of truth
 * as the Stock Overview screens) and streamed back as .xlsx. Column widths are set
 * explicitly rather than via POI's autoSizeColumn(), which depends on AWT font
 * metrics that aren't reliably available in a slim/headless container image.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ProductRepository productRepository;
    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final OrderRepository orderRepository;

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public byte[] generateStockLevelsReport() {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Stock Levels");
            CellStyle headerStyle = headerStyle(workbook);

            String[] headers = {"SKU", "Product Name", "Tracking Type", "Location", "Available", "Quarantined", "Allocated", "Despatched", "Returned", "Total"};
            writeHeaderRow(sheet, headers, headerStyle);

            int[] widths = {4000, 9000, 3000, 3000, 3000, 3200, 3000, 3000, 3000, 2500};
            setColumnWidths(sheet, widths);

            List<Product> products = productRepository.findAll();
            products.sort(Comparator.comparing(Product::getSku));

            int rowNum = 1;
            for (Product product : products) {
                List<StockItem> items = stockItemRepository.findByProduct_Id(product.getId());
                Map<String, int[]> byLocation = new TreeMap<>();
                for (StockItem item : items) {
                    String locCode = item.getLocation() != null ? item.getLocation().getCode() : "(no location)";
                    int[] counts = byLocation.computeIfAbsent(locCode, k -> new int[5]);
                    switch (item.getStatus()) {
                        case AVAILABLE -> counts[0]++;
                        case QUARANTINED -> counts[1]++;
                        case ALLOCATED -> counts[2]++;
                        case DESPATCHED -> counts[3]++;
                        case RETURNED -> counts[4]++;
                    }
                }
                if (byLocation.isEmpty()) continue;

                for (Map.Entry<String, int[]> entry : byLocation.entrySet()) {
                    int[] c = entry.getValue();
                    int total = c[0] + c[1] + c[2] + c[3] + c[4];
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(product.getSku());
                    row.createCell(1).setCellValue(product.getName());
                    row.createCell(2).setCellValue(product.getTrackingType().name());
                    row.createCell(3).setCellValue(entry.getKey());
                    row.createCell(4).setCellValue(c[0]);
                    row.createCell(5).setCellValue(c[1]);
                    row.createCell(6).setCellValue(c[2]);
                    row.createCell(7).setCellValue(c[3]);
                    row.createCell(8).setCellValue(c[4]);
                    row.createCell(9).setCellValue(total);
                }
            }

            return toBytes(workbook);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public byte[] generateStockItemsReport() {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Stock Items");
            CellStyle headerStyle = headerStyle(workbook);

            String[] headers = {"SKU", "Product Name", "MAC Address", "Serial Number", "WiFi MAC", "Batch/Carton", "Location", "Status", "Received At"};
            writeHeaderRow(sheet, headers, headerStyle);

            int[] widths = {4000, 9000, 5000, 5000, 5000, 5000, 3000, 3200, 5000};
            setColumnWidths(sheet, widths);

            List<StockItem> items = stockItemRepository.findAll();
            items.sort(Comparator
                    .comparing((StockItem i) -> i.getProduct().getSku())
                    .thenComparing(i -> Optional.ofNullable(i.getMacAddress()).orElse(Optional.ofNullable(i.getSerialNumber()).orElse(""))));

            int rowNum = 1;
            for (StockItem item : items) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(item.getProduct().getSku());
                row.createCell(1).setCellValue(item.getProduct().getName());
                row.createCell(2).setCellValue(nullToBlank(item.getMacAddress()));
                row.createCell(3).setCellValue(nullToBlank(item.getSerialNumber()));
                row.createCell(4).setCellValue(nullToBlank(item.getWifiMacAddress()));
                row.createCell(5).setCellValue(nullToBlank(item.getBatchCode()));
                row.createCell(6).setCellValue(item.getLocation() != null ? item.getLocation().getCode() : "");
                row.createCell(7).setCellValue(item.getStatus().name());
                row.createCell(8).setCellValue(item.getReceivedAt() != null ? item.getReceivedAt().format(TS_FORMAT) : "");
            }

            return toBytes(workbook);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public byte[] generateMovementsReport(LocalDate from, LocalDate to) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Stock Movements");
            CellStyle headerStyle = headerStyle(workbook);

            String[] headers = {"Date/Time", "Type", "SKU", "Product Name", "MAC/Serial", "From Location", "To Location", "Quantity", "Reference", "Notes", "By"};
            writeHeaderRow(sheet, headers, headerStyle);

            int[] widths = {5200, 3200, 4000, 9000, 5000, 3200, 3200, 2500, 4000, 6000, 3200};
            setColumnWidths(sheet, widths);

            LocalDateTime fromDt = from != null ? from.atStartOfDay() : LocalDateTime.MIN;
            LocalDateTime toDt = to != null ? to.plusDays(1).atStartOfDay() : LocalDateTime.MAX;

            List<StockMovement> movements = stockMovementRepository.findAll().stream()
                    .filter(m -> !m.getCreatedAt().isBefore(fromDt) && m.getCreatedAt().isBefore(toDt))
                    .sorted(Comparator.comparing(StockMovement::getCreatedAt).reversed())
                    .toList();

            int rowNum = 1;
            for (StockMovement m : movements) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(m.getCreatedAt().format(TS_FORMAT));
                row.createCell(1).setCellValue(m.getMovementType().name());
                row.createCell(2).setCellValue(m.getProduct().getSku());
                row.createCell(3).setCellValue(m.getProduct().getName());
                String identifier = m.getStockItem() != null
                        ? Optional.ofNullable(m.getStockItem().getMacAddress()).orElse(Optional.ofNullable(m.getStockItem().getSerialNumber()).orElse(""))
                        : "";
                row.createCell(4).setCellValue(identifier);
                row.createCell(5).setCellValue(m.getFromLocation() != null ? m.getFromLocation().getCode() : "");
                row.createCell(6).setCellValue(m.getToLocation() != null ? m.getToLocation().getCode() : "");
                row.createCell(7).setCellValue(m.getQuantity());
                row.createCell(8).setCellValue(nullToBlank(m.getReference()));
                row.createCell(9).setCellValue(nullToBlank(m.getNotes()));
                row.createCell(10).setCellValue(nullToBlank(m.getCreatedBy()));
            }

            return toBytes(workbook);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public byte[] generateOpenOrdersReport() {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Open Orders");
            CellStyle headerStyle = headerStyle(workbook);

            String[] headers = {"Order Number", "Order Date", "Customer Name", "Order Reference", "Ecommerce Order #", "Ordered By", "Delivery Name", "Delivery Town", "Delivery Country", "Status", "Order Type", "Line Count"};
            writeHeaderRow(sheet, headers, headerStyle);

            int[] widths = {4000, 5200, 7000, 4000, 4800, 4000, 6000, 4000, 4000, 5000, 3200, 2800};
            setColumnWidths(sheet, widths);

            List<Order> orders = orderRepository.findAll().stream()
                    .filter(o -> o.getStatus() != OrderStatus.COMPLETED && o.getStatus() != OrderStatus.CANCELLED)
                    .sorted(Comparator.comparing(Order::getOrderDate))
                    .toList();

            int rowNum = 1;
            for (Order o : orders) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(o.getOrderNumber());
                row.createCell(1).setCellValue(o.getOrderDate().format(TS_FORMAT));
                row.createCell(2).setCellValue(o.getCustomerName());
                row.createCell(3).setCellValue(nullToBlank(o.getOrderReference()));
                row.createCell(4).setCellValue(nullToBlank(o.getEcommerceOrderNumber()));
                row.createCell(5).setCellValue(nullToBlank(o.getOrderedBy()));
                row.createCell(6).setCellValue(nullToBlank(o.getDeliveryName()));
                row.createCell(7).setCellValue(nullToBlank(o.getDeliveryTown()));
                row.createCell(8).setCellValue(nullToBlank(o.getDeliveryCountry()));
                row.createCell(9).setCellValue(o.getStatus().name());
                row.createCell(10).setCellValue(o.getOrderType().name());
                row.createCell(11).setCellValue(o.getLines().size());
            }

            return toBytes(workbook);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public byte[] generateOrderLineDetailReport() {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Order Line Detail");
            CellStyle headerStyle = headerStyle(workbook);

            String[] headers = {"Order Number", "Order Date", "Customer Name", "Status", "Order Type", "SKU", "Product Name", "Qty Ordered", "Qty Despatched", "Unit Price", "Line Total", "Notes"};
            writeHeaderRow(sheet, headers, headerStyle);

            int[] widths = {4000, 5200, 7000, 5000, 3200, 4000, 8000, 3000, 3400, 3000, 3000, 5000};
            setColumnWidths(sheet, widths);

            List<Order> orders = orderRepository.findAll();
            orders.sort(Comparator.comparing(Order::getOrderDate).reversed());

            int rowNum = 1;
            for (Order o : orders) {
                for (OrderLine line : o.getLines()) {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(o.getOrderNumber());
                    row.createCell(1).setCellValue(o.getOrderDate().format(TS_FORMAT));
                    row.createCell(2).setCellValue(o.getCustomerName());
                    row.createCell(3).setCellValue(o.getStatus().name());
                    row.createCell(4).setCellValue(o.getOrderType().name());
                    row.createCell(5).setCellValue(line.getProduct().getSku());
                    row.createCell(6).setCellValue(line.getProduct().getName());
                    row.createCell(7).setCellValue(line.getQuantityOrdered());
                    row.createCell(8).setCellValue(line.getQuantityDespatched());
                    double unitPrice = line.getUnitPrice() != null ? line.getUnitPrice().doubleValue() : 0;
                    row.createCell(9).setCellValue(unitPrice);
                    row.createCell(10).setCellValue(unitPrice * line.getQuantityOrdered());
                    row.createCell(11).setCellValue(nullToBlank(line.getNotes()));
                }
            }

            return toBytes(workbook);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void writeHeaderRow(Sheet sheet, String[] headers, CellStyle style) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
        sheet.createFreezePane(0, 1);
    }

    private void setColumnWidths(Sheet sheet, int[] widths) {
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i]);
        }
    }

    private String nullToBlank(String value) {
        return value != null ? value : "";
    }

    private byte[] toBytes(XSSFWorkbook workbook) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
