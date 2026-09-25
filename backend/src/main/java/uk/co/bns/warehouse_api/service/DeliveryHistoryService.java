package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import uk.co.bns.warehouse_api.dto.DeliveryHistoryCartonSummaryRow;
import uk.co.bns.warehouse_api.dto.DeliveryHistoryDetailView;
import uk.co.bns.warehouse_api.dto.DeliveryHistoryItemView;
import uk.co.bns.warehouse_api.dto.DeliveryHistoryView;
import uk.co.bns.warehouse_api.dto.ShipmentView;
import uk.co.bns.warehouse_api.entity.Carton;
import uk.co.bns.warehouse_api.entity.CartonLine;
import uk.co.bns.warehouse_api.entity.Order;
import uk.co.bns.warehouse_api.entity.OrderLine;
import uk.co.bns.warehouse_api.entity.StockItem;
import uk.co.bns.warehouse_api.enums.StockItemStatus;
import uk.co.bns.warehouse_api.enums.TrackingType;
import uk.co.bns.warehouse_api.exception.NotFoundException;
import uk.co.bns.warehouse_api.repository.CartonLineRepository;
import uk.co.bns.warehouse_api.repository.CartonRepository;
import uk.co.bns.warehouse_api.repository.OrderRepository;
import uk.co.bns.warehouse_api.repository.ShipmentRepository;
import uk.co.bns.warehouse_api.repository.StockItemRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sales &rarr; Delivery History - every order that's actually been despatched at
 * least once (Order.despatchedAt), with what actually shipped down to the
 * individual MAC/serial/batch and which carton it was packed into. A
 * separate page from Despatch itself (which only ever shows what's still
 * ready to pack) and from Stock Trace (which is per-item, not per-order).
 */
@Service
@RequiredArgsConstructor
public class DeliveryHistoryService {

    private final OrderRepository orderRepository;
    private final StockItemRepository stockItemRepository;
    private final CartonRepository cartonRepository;
    private final CartonLineRepository cartonLineRepository;
    private final ShipmentRepository shipmentRepository;

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public List<DeliveryHistoryView> list(LocalDate from, LocalDate to) {
        LocalDateTime fromDt = from != null ? from.atStartOfDay() : LocalDateTime.MIN;
        LocalDateTime toDt = to != null ? to.plusDays(1).atStartOfDay() : LocalDateTime.MAX;

        return orderRepository.findByDespatchedAtIsNotNullOrderByDespatchedAtDesc().stream()
                .filter(o -> !o.getDespatchedAt().isBefore(fromDt) && o.getDespatchedAt().isBefore(toDt))
                .map(this::toView)
                .toList();
    }

    public DeliveryHistoryDetailView detail(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order " + orderId + " not found"));
        if (order.getDespatchedAt() == null) {
            throw new NotFoundException("Order " + order.getOrderNumber() + " hasn't been despatched yet");
        }
        return new DeliveryHistoryDetailView(toView(order), itemsFor(order), cartonSummaryFor(order), previousShipmentsFor(order));
    }

    private List<ShipmentView> previousShipmentsFor(Order order) {
        return shipmentRepository.findByOrder_IdOrderByCreatedAtAsc(order.getId()).stream()
                .map(s -> new ShipmentView(
                        s.getShippedAt(),
                        s.getDpdConsignmentNumber() != null ? "DPD" : null,
                        s.getCourierMethod(),
                        s.getDpdConsignmentNumber(),
                        s.getDpdParcelNumbers(),
                        s.getShippingCost()))
                .toList();
    }

    public byte[] exportExcel(LocalDate from, LocalDate to) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Delivery History");
            CellStyle headerStyle = headerStyle(workbook);

            String[] headers = {"Order Number", "Despatched", "Company", "Delivery Name", "Delivery Postcode",
                    "Courier", "Delivery Method", "Consignment Number", "Parcels", "Status"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                var cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            sheet.createFreezePane(0, 1);
            int[] widths = {4000, 5200, 7000, 6000, 3500, 3000, 6000, 5000, 2500, 5000};
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i]);
            }

            int rowNum = 1;
            for (DeliveryHistoryView v : list(from, to)) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(v.orderNumber());
                row.createCell(1).setCellValue(v.despatchedAt().format(TS_FORMAT));
                row.createCell(2).setCellValue(nullToBlank(v.companyName()));
                row.createCell(3).setCellValue(nullToBlank(v.deliveryName()));
                row.createCell(4).setCellValue(nullToBlank(v.deliveryPostcode()));
                row.createCell(5).setCellValue(nullToBlank(v.courier()));
                row.createCell(6).setCellValue(nullToBlank(v.deliveryMethod()));
                row.createCell(7).setCellValue(nullToBlank(v.consignmentNumber()));
                row.createCell(8).setCellValue(v.parcelCount());
                row.createCell(9).setCellValue(v.orderStatus());
            }

            return toBytes(workbook);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public byte[] exportDetailExcel(Long orderId) {
        DeliveryHistoryDetailView detail = detail(orderId);
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Order " + detail.order().orderNumber());
            CellStyle headerStyle = headerStyle(workbook);

            String[] headers = {"SKU", "Product Name", "MAC Address", "Serial Number", "WiFi MAC", "Batch Code",
                    "Quantity", "Carton Number"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                var cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            sheet.createFreezePane(0, 1);
            int[] widths = {4000, 9000, 5000, 5000, 5000, 5000, 3000, 3500};
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i]);
            }

            int rowNum = 1;
            for (DeliveryHistoryItemView item : detail.items()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(item.sku());
                row.createCell(1).setCellValue(item.productName());
                row.createCell(2).setCellValue(nullToBlank(item.macAddress()));
                row.createCell(3).setCellValue(nullToBlank(item.serialNumber()));
                row.createCell(4).setCellValue(nullToBlank(item.wifiMacAddress()));
                row.createCell(5).setCellValue(nullToBlank(item.batchCode()));
                row.createCell(6).setCellValue(item.quantity());
                row.createCell(7).setCellValue(item.cartonNumber() != null ? String.valueOf(item.cartonNumber()) : "");
            }

            Sheet summarySheet = workbook.createSheet("Carton Summary");
            CellStyle summaryHeaderStyle = headerStyle(workbook);
            String[] summaryHeaders = {"Carton Number", "SKU", "Product Name", "Quantity"};
            Row summaryHeader = summarySheet.createRow(0);
            for (int i = 0; i < summaryHeaders.length; i++) {
                var cell = summaryHeader.createCell(i);
                cell.setCellValue(summaryHeaders[i]);
                cell.setCellStyle(summaryHeaderStyle);
            }
            summarySheet.createFreezePane(0, 1);
            int[] summaryWidths = {3500, 4000, 9000, 3000};
            for (int i = 0; i < summaryWidths.length; i++) {
                summarySheet.setColumnWidth(i, summaryWidths[i]);
            }
            int summaryRowNum = 1;
            for (DeliveryHistoryCartonSummaryRow row : detail.cartonSummary()) {
                Row r = summarySheet.createRow(summaryRowNum++);
                r.createCell(0).setCellValue(row.cartonNumber());
                r.createCell(1).setCellValue(row.sku());
                r.createCell(2).setCellValue(row.productName());
                r.createCell(3).setCellValue(row.quantity());
            }

            return toBytes(workbook);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private DeliveryHistoryView toView(Order order) {
        int parcelCount = cartonRepository.countByOrder_Id(order.getId());
        // Only DPD is actually wired up today (see DpdShippingService) - a
        // consignment number is the tell for "this genuinely went out on
        // DPD" as opposed to a manual/no-courier despatch (e.g. collected,
        // or a courier typed straight onto a carton's tracking number).
        String courier = order.getDpdConsignmentNumber() != null ? "DPD" : null;
        return new DeliveryHistoryView(
                order.getId(), order.getOrderNumber(), order.getDespatchedAt(),
                order.getCompany() != null ? order.getCompany().getName() : null,
                order.getDeliveryName(), order.getDeliveryPostcode(),
                courier, order.getCourierMethod(), order.getDpdConsignmentNumber(),
                parcelCount, order.getStatus().name());
    }

    private List<DeliveryHistoryItemView> itemsFor(Order order) {
        List<DeliveryHistoryItemView> rows = new ArrayList<>();

        List<StockItem> despatchedItems = stockItemRepository.findByOrderLine_Order_IdAndStatus(
                order.getId(), StockItemStatus.DESPATCHED);
        if (!despatchedItems.isEmpty()) {
            List<CartonLine> cartonLines = cartonLineRepository.findByOrderLine_Order_Id(order.getId());
            Map<Long, List<CartonLine>> cartonLinesByOrderLine = cartonLines.stream()
                    .filter(cl -> cl.getOrderLine() != null)
                    .collect(Collectors.groupingBy(cl -> cl.getOrderLine().getId()));

            for (StockItem item : despatchedItems) {
                Integer cartonNumber = null;
                if (item.getCarton() != null) {
                    // SERIAL packing mode - exact, every time.
                    cartonNumber = item.getCarton().getCartonNumber();
                } else if (item.getOrderLine() != null) {
                    // SPLIT packing mode - CartonLine tracks carton per
                    // quantity-slice of the line, not per specific serial, so
                    // this is only unambiguous when the whole line went into
                    // one carton.
                    Set<Integer> distinctCartons = cartonLinesByOrderLine
                            .getOrDefault(item.getOrderLine().getId(), List.of()).stream()
                            .map(CartonLine::getCarton)
                            .filter(Objects::nonNull)
                            .map(Carton::getCartonNumber)
                            .collect(Collectors.toSet());
                    if (distinctCartons.size() == 1) {
                        cartonNumber = distinctCartons.iterator().next();
                    }
                }
                rows.add(new DeliveryHistoryItemView(
                        item.getProduct().getSku(), item.getProduct().getName(),
                        item.getMacAddress(), item.getSerialNumber(), item.getWifiMacAddress(), item.getBatchCode(),
                        1, cartonNumber));
            }
        }

        // Quantity-only products have no per-unit StockItem identity to
        // report - shown instead as one row per carton they were actually
        // packed into, straight from CartonLine, which IS exact at that
        // granularity regardless of packing mode.
        for (OrderLine line : order.getLines()) {
            if (line.getProduct().getTrackingType() != TrackingType.NONE) continue;
            if (line.getQuantityDespatched() <= 0) continue;
            for (CartonLine cl : cartonLineRepository.findByOrderLine_Id(line.getId())) {
                if (cl.getCarton() == null || cl.getQuantity() == null || cl.getQuantity() <= 0) continue;
                rows.add(new DeliveryHistoryItemView(
                        line.getProduct().getSku(), line.getProduct().getName(),
                        null, null, null, null,
                        cl.getQuantity(), cl.getCarton().getCartonNumber()));
            }
        }

        rows.sort(Comparator.comparing(DeliveryHistoryItemView::sku));
        return rows;
    }

    /**
     * Per-carton SKU quantities, for the "Carton Summary" dropdown above the
     * item table. Combines the same two sources as itemsFor() above - a
     * Serial-Packing StockItem's own carton, or a Split-Packing/NONE-tracking
     * CartonLine - but summed per carton+product rather than listed per
     * unit. Unlike the per-unit view, this is never ambiguous: whichever
     * packing mode was used, every packed unit ends up counted against
     * exactly one carton here (a StockItem contributes to its own carton, a
     * CartonLine's quantity to its carton - the two sources never overlap
     * for the same units, since Split Packing never sets StockItem.carton
     * and Serial Packing never creates CartonLines).
     */
    private List<DeliveryHistoryCartonSummaryRow> cartonSummaryFor(Order order) {
        record Key(int cartonNumber, String sku, String productName) {}
        Map<Key, Integer> totals = new java.util.LinkedHashMap<>();

        List<StockItem> despatchedItems = stockItemRepository.findByOrderLine_Order_IdAndStatus(
                order.getId(), StockItemStatus.DESPATCHED);
        for (StockItem item : despatchedItems) {
            if (item.getCarton() == null) continue;
            Key key = new Key(item.getCarton().getCartonNumber(), item.getProduct().getSku(), item.getProduct().getName());
            totals.merge(key, 1, Integer::sum);
        }

        List<CartonLine> cartonLines = cartonLineRepository.findByOrderLine_Order_Id(order.getId());
        for (CartonLine cl : cartonLines) {
            if (cl.getCarton() == null || cl.getOrderLine() == null || cl.getQuantity() == null || cl.getQuantity() <= 0) continue;
            var product = cl.getOrderLine().getProduct();
            Key key = new Key(cl.getCarton().getCartonNumber(), product.getSku(), product.getName());
            totals.merge(key, cl.getQuantity(), Integer::sum);
        }

        return totals.entrySet().stream()
                .map(e -> new DeliveryHistoryCartonSummaryRow(e.getKey().cartonNumber(), e.getKey().sku(), e.getKey().productName(), e.getValue()))
                .sorted(Comparator.comparingInt(DeliveryHistoryCartonSummaryRow::cartonNumber)
                        .thenComparing(DeliveryHistoryCartonSummaryRow::sku))
                .toList();
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
