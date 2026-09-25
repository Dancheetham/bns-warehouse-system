package uk.co.bns.warehouse_api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.co.bns.warehouse_api.dto.DeliveryHistoryDetailView;
import uk.co.bns.warehouse_api.dto.DeliveryHistoryView;
import uk.co.bns.warehouse_api.service.DeliveryHistoryService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/delivery-history")
@RequiredArgsConstructor
public class DeliveryHistoryController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final DeliveryHistoryService deliveryHistoryService;

    @GetMapping
    public List<DeliveryHistoryView> list(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return deliveryHistoryService.list(from, to);
    }

    @GetMapping("/{orderId}")
    public DeliveryHistoryDetailView detail(@PathVariable Long orderId) {
        return deliveryHistoryService.detail(orderId);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        byte[] data = deliveryHistoryService.exportExcel(from, to);
        return download(data, "delivery-history-" + LocalDate.now() + ".xlsx");
    }

    @GetMapping("/{orderId}/export")
    public ResponseEntity<byte[]> exportDetail(@PathVariable Long orderId) {
        byte[] data = deliveryHistoryService.exportDetailExcel(orderId);
        return download(data, "delivery-history-order-" + orderId + ".xlsx");
    }

    private ResponseEntity<byte[]> download(byte[] data, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(XLSX)
                .body(data);
    }
}
