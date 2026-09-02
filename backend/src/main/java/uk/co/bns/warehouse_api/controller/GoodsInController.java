package uk.co.bns.warehouse_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.dto.ScanCartonRequest;
import uk.co.bns.warehouse_api.dto.ScanCartonResult;
import uk.co.bns.warehouse_api.entity.GoodsInSession;
import uk.co.bns.warehouse_api.service.GoodsInService;

import java.util.List;

@RestController
@RequestMapping("/api/goods-in")
@RequiredArgsConstructor
public class GoodsInController {

    private final GoodsInService goodsInService;

    public record StartSessionRequest(Long purchaseOrderId, Long locationId, String startedBy) {}
    public record SaveSessionRequest(String savedBy) {}

    @GetMapping("/sessions/open")
    public List<GoodsInSession> openSessions() {
        return goodsInService.getOpenSessions();
    }

    @GetMapping("/sessions/{sessionId}")
    public GoodsInSession getSession(@PathVariable Long sessionId) {
        return goodsInService.getSession(sessionId);
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public GoodsInSession startSession(@RequestBody StartSessionRequest request) {
        return goodsInService.startSession(request.purchaseOrderId(), request.locationId(), request.startedBy());
    }

    @PostMapping("/sessions/{sessionId}/scan")
    public ScanCartonResult scanCarton(@PathVariable Long sessionId, @Valid @RequestBody ScanCartonRequest request) {
        return goodsInService.scanCarton(sessionId, request);
    }

    @PostMapping("/sessions/{sessionId}/save")
    public GoodsInSession saveSession(@PathVariable Long sessionId, @RequestBody SaveSessionRequest request) {
        return goodsInService.saveSession(sessionId, request.savedBy());
    }
}
