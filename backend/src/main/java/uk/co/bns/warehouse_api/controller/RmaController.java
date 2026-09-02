package uk.co.bns.warehouse_api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.dto.*;
import uk.co.bns.warehouse_api.entity.RmaRequest;
import uk.co.bns.warehouse_api.enums.RmaStatus;
import uk.co.bns.warehouse_api.service.RmaCoverSheetService;
import uk.co.bns.warehouse_api.service.RmaService;

import java.util.List;

@RestController
@RequestMapping("/api/rma")
@RequiredArgsConstructor
public class RmaController {

    private final RmaService rmaService;
    private final RmaCoverSheetService coverSheetService;

    @GetMapping
    public List<RmaSummaryView> list(@RequestParam(required = false) RmaStatus status) {
        return rmaService.listByStatus(status).stream().map(rmaService::toSummary).toList();
    }

    @GetMapping("/{id}")
    public RmaDetailView get(@PathVariable Long id) {
        return rmaService.toDetail(rmaService.getDetail(id));
    }

    @PostMapping("/{id}/approve")
    public RmaDetailView approve(@PathVariable Long id, @RequestBody ApproveRmaRequest request) {
        RmaRequest rma = rmaService.approve(id, request);
        return rmaService.toDetail(rma);
    }

    @PostMapping("/{id}/reject")
    public RmaDetailView reject(@PathVariable Long id, @RequestBody RejectRmaRequest request) {
        RmaRequest rma = rmaService.reject(id, request);
        return rmaService.toDetail(rma);
    }

    @PostMapping("/{id}/receive")
    public RmaDetailView receive(@PathVariable Long id, @RequestBody ReceiveRmaRequest request) {
        RmaRequest rma = rmaService.receive(id, request);
        return rmaService.toDetail(rma);
    }

    @GetMapping("/{id}/cover-sheet")
    public ResponseEntity<byte[]> coverSheet(@PathVariable Long id) {
        byte[] pdf = coverSheetService.generate(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"rma-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
