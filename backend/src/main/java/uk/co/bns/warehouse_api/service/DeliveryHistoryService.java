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
import uk.co.bns.warehouse_api.dto.DeliveryHistoryDetailView;
import uk.co.bns.warehouse_api.dto.DeliveryHistoryItemView;
import uk.co.bns.warehouse_api.dto.DeliveryHistoryView;
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
        return new DeliveryHistoryDetailView(toView(order), itemsFor(order));
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
