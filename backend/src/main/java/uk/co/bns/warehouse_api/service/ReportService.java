package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.AxisCrosses;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.LegendPosition;
import org.apache.poi.xddf.usermodel.chart.MarkerStyle;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFChartLegend;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFLineChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import uk.co.bns.warehouse_api.dto.InvoicedMonthValue;
import uk.co.bns.warehouse_api.entity.*;
import uk.co.bns.warehouse_api.enums.OrderStatus;
import uk.co.bns.warehouse_api.enums.OrderType;
import uk.co.bns.warehouse_api.enums.StockItemStatus;
import uk.co.bns.warehouse_api.repository.OrderRepository;
import uk.co.bns.warehouse_api.repository.ProductRepository;
import uk.co.bns.warehouse_api.repository.StockItemRepository;
import uk.co.bns.warehouse_api.repository.StockMovementRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
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
    private final CompanyService companyService;

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // "Invoiced" here means an order that actually represents money changing
    // hands - ORDER (a normal sale) or CREDIT_REFUND (money back) - not a
    // QUOTE, PAUSED order, or SCHEDULED order that was never placed, and not
    // a CANCELLED order that was never actually fulfilled/invoiced.
    private boolean countsAsInvoiced(Order order) {
        return (order.getOrderType() == OrderType.ORDER || order.getOrderType() == OrderType.CREDIT_REFUND)
                && order.getStatus() != OrderStatus.CANCELLED;
    }

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

    /**
     * Monthly invoiced (ORDER) vs credited (CREDIT_REFUND) net value for one
     * calendar year - the data behind the Dashboard's "Invoiced Values by
     * Month" chart. Always returns exactly 12 entries (Jan-Dec), zero-filled
     * for months with nothing invoiced, so the frontend never has to handle
     * missing months.
     */
    public List<InvoicedMonthValue> invoicedValuesByMonth(int year) {
        BigDecimal[] invoiceTotals = new BigDecimal[12];
        BigDecimal[] creditTotals = new BigDecimal[12];
        Arrays.fill(invoiceTotals, BigDecimal.ZERO);
        Arrays.fill(creditTotals, BigDecimal.ZERO);

        for (Order order : orderRepository.findAll()) {
            if (!countsAsInvoiced(order)) continue;
            if (order.getOrderDate() == null || order.getOrderDate().getYear() != year) continue;

            int monthIndex = order.getOrderDate().getMonthValue() - 1;
            BigDecimal value = companyService.orderTotal(order);
            if (order.getOrderType() == OrderType.CREDIT_REFUND) {
                creditTotals[monthIndex] = creditTotals[monthIndex].add(value);
            } else {
                invoiceTotals[monthIndex] = invoiceTotals[monthIndex].add(value);
            }
        }

        List<InvoicedMonthValue> result = new ArrayList<>(12);
        for (int m = 0; m < 12; m++) {
            result.add(new InvoicedMonthValue(m + 1, invoiceTotals[m], creditTotals[m]));
        }
        return result;
    }

    /**
     * The exportable version of the same data, one row per order (not
     * aggregated by month) - filterable by invoice date range, invoice
     * and/or credit, and a specific company, matching the old OrderWise
     * "Invoiced Values by Month" report's own filters.
     */
    public byte[] generateInvoiceReport(LocalDate from, LocalDate to, boolean includeInvoices,
                                         boolean includeCredits, Long companyId) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Invoice Report");
            CellStyle headerStyle = headerStyle(workbook);

            String[] headers = {"Order Number", "Invoice Date", "Type", "Customer Name", "Company", "Goods Net", "Delivery Net", "Total Net"};
            writeHeaderRow(sheet, headers, headerStyle);

            int[] widths = {4000, 4000, 3200, 7000, 7000, 3200, 3200, 3200};
            setColumnWidths(sheet, widths);

            LocalDateTime fromDt = from != null ? from.atStartOfDay() : LocalDateTime.MIN;
            LocalDateTime toDt = to != null ? to.plusDays(1).atStartOfDay() : LocalDateTime.MAX;

            List<Order> orders = orderRepository.findAll().stream()
                    .filter(this::countsAsInvoiced)
                    .filter(o -> o.getOrderDate() != null && !o.getOrderDate().isBefore(fromDt) && o.getOrderDate().isBefore(toDt))
                    .filter(o -> includeInvoices || o.getOrderType() != OrderType.ORDER)
                    .filter(o -> includeCredits || o.getOrderType() != OrderType.CREDIT_REFUND)
                    .filter(o -> companyId == null || (o.getCompany() != null && companyId.equals(o.getCompany().getId())))
                    .sorted(Comparator.comparing(Order::getOrderDate))
                    .toList();

            int rowNum = 1;
            for (Order o : orders) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(o.getOrderNumber());
                row.createCell(1).setCellValue(o.getOrderDate().format(DATE_FORMAT));
                row.createCell(2).setCellValue(o.getOrderType() == OrderType.CREDIT_REFUND ? "Credit" : "Invoice");
                row.createCell(3).setCellValue(o.getCustomerName());
                row.createCell(4).setCellValue(o.getCompany() != null ? o.getCompany().getName() : "");
                row.createCell(5).setCellValue(companyService.goodsTotal(o).doubleValue());
                row.createCell(6).setCellValue(companyService.deliveryTotal(o).doubleValue());
                row.createCell(7).setCellValue(companyService.orderTotal(o).doubleValue());
            }

            // A "by month" view only means something for a single calendar
            // year, so the chart always covers the year the filter's "from"
            // date falls in (or the current year, with no date filter set) -
            // same basis as the Dashboard's own chart - rather than trying
            // to chart an arbitrary custom date range month-by-month.
            int chartYear = from != null ? from.getYear() : LocalDate.now().getYear();
            addMonthlyChartSheet(workbook, chartYear, invoicedValuesByMonth(chartYear));

            return toBytes(workbook);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A second sheet alongside the row-by-row export: the same monthly
     * invoiced-vs-credited figures as the Dashboard chart, as both a small
     * data table and a native Excel line chart built from it (not a picture
     * of a chart), so it can be opened, styled, or copied like any other
     * Excel chart. The exact value for each point is in the data table
     * immediately to its left rather than in an on-chart label - POI's
     * high-level chart API (XDDF) doesn't expose a "show data labels"
     * setter, and reaching around it via the raw OOXML schema classes
     * (CTLineChart/CTDLbls) needs a dependency (poi-ooxml-full) that failed
     * to resolve cleanly in this project's Docker build, so that approach
     * was dropped rather than risk another broken build - see CHANGELOG.
     */
    private void addMonthlyChartSheet(XSSFWorkbook workbook, int year, List<InvoicedMonthValue> monthly) {
        XSSFSheet dataSheet = workbook.createSheet("Monthly Chart");
        CellStyle headerStyle = headerStyle(workbook);
        writeHeaderRow(dataSheet, new String[]{"Month", "Invoiced", "Credited"}, headerStyle);
        setColumnWidths(dataSheet, new int[]{3000, 3500, 3500});

        String[] monthNames = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        for (int i = 0; i < monthly.size(); i++) {
            InvoicedMonthValue m = monthly.get(i);
            Row row = dataSheet.createRow(i + 1);
            row.createCell(0).setCellValue(monthNames[m.month() - 1]);
            row.createCell(1).setCellValue(m.invoiceTotal().doubleValue());
            row.createCell(2).setCellValue(m.creditTotal().doubleValue());
        }
        int lastRow = monthly.size(); // header is row 0, data is rows 1..monthly.size()

        XSSFDrawing drawing = dataSheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 4, 0, 16, 22);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText("Invoiced Values by Month - " + year);
        chart.setTitleOverlay(false);

        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.BOTTOM);

        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setCrosses(AxisCrosses.AUTO_ZERO);

        XDDFDataSource<String> monthLabels = XDDFDataSourcesFactory.fromStringCellRange(dataSheet,
                new CellRangeAddress(1, lastRow, 0, 0));
        XDDFNumericalDataSource<Double> invoicedValues = XDDFDataSourcesFactory.fromNumericCellRange(dataSheet,
                new CellRangeAddress(1, lastRow, 1, 1));
        XDDFNumericalDataSource<Double> creditedValues = XDDFDataSourcesFactory.fromNumericCellRange(dataSheet,
                new CellRangeAddress(1, lastRow, 2, 2));

        XDDFLineChartData chartData = (XDDFLineChartData) chart.createData(ChartTypes.LINE, bottomAxis, leftAxis);

        XDDFLineChartData.Series invoicedSeries = (XDDFLineChartData.Series) chartData.addSeries(monthLabels, invoicedValues);
        invoicedSeries.setTitle("Invoiced", null);
        invoicedSeries.setSmooth(false);
        invoicedSeries.setMarkerStyle(MarkerStyle.CIRCLE);

        XDDFLineChartData.Series creditedSeries = (XDDFLineChartData.Series) chartData.addSeries(monthLabels, creditedValues);
        creditedSeries.setTitle("Credited", null);
        creditedSeries.setSmooth(false);
        creditedSeries.setMarkerStyle(MarkerStyle.CIRCLE);

        chart.plot(chartData);
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
