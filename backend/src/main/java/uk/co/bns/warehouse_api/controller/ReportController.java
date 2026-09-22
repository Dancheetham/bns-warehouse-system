package uk.co.bns.warehouse_api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.co.bns.warehouse_api.dto.InvoicedMonthValue;
import uk.co.bns.warehouse_api.service.ReportService;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ReportService reportService;

    @GetMapping("/stock-levels")
    public ResponseEntity<byte[]> stockLevels() {
        byte[] data = reportService.generateStockLevelsReport();
        return download(data, "stock-levels-" + LocalDate.now() + ".xlsx");
    }

    @GetMapping("/stock-items")
    public ResponseEntity<byte[]> stockItems() {
        byte[] data = reportService.generateStockItemsReport();
        return download(data, "stock-items-" + LocalDate.now() + ".xlsx");
    }

    @GetMapping("/movements")
    public ResponseEntity<byte[]> movements(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        byte[] data = reportService.generateMovementsReport(from, to);
        return download(data, "stock-movements-" + LocalDate.now() + ".xlsx");
    }

    @GetMapping("/open-orders")
    public ResponseEntity<byte[]> openOrders() {
        byte[] data = reportService.generateOpenOrdersReport();
        return download(data, "open-orders-" + LocalDate.now() + ".xlsx");
    }

    @GetMapping("/order-lines")
    public ResponseEntity<byte[]> orderLines() {
        byte[] data = reportService.generateOrderLineDetailReport();
        return download(data, "order-line-detail-" + LocalDate.now() + ".xlsx");
    }

    // JSON, not xlsx - feeds the Dashboard's "Invoiced Values by Month" chart
    // directly, one calendar year at a time (defaults to the current year).
    @GetMapping("/invoiced-values-by-month")
    public List<InvoicedMonthValue> invoicedValuesByMonth(
            @RequestParam(required = false) Integer year) {
        return reportService.invoicedValuesByMonth(year != null ? year : Year.now().getValue());
    }

    @GetMapping("/invoices")
    public ResponseEntity<byte[]> invoices(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "true") boolean includeInvoices,
            @RequestParam(defaultValue = "true") boolean includeCredits,
            @RequestParam(required = false) Long companyId) {
        byte[] data = reportService.generateInvoiceReport(from, to, includeInvoices, includeCredits, companyId);
        return download(data, "invoice-report-" + LocalDate.now() + ".xlsx");
    }

    private ResponseEntity<byte[]> download(byte[] data, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(XLSX)
                .body(data);
    }
}
