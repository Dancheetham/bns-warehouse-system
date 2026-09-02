package uk.co.bns.warehouse_api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.co.bns.warehouse_api.dto.StockTraceResult;
import uk.co.bns.warehouse_api.service.StockTraceService;

@RestController
@RequestMapping("/api/trace")
@RequiredArgsConstructor
public class StockTraceController {

    private final StockTraceService stockTraceService;

    @GetMapping("/mac/{mac}")
    public StockTraceResult byMac(@PathVariable String mac) {
        return stockTraceService.traceByMac(mac);
    }

    @GetMapping("/serial/{serial}")
    public StockTraceResult bySerial(@PathVariable String serial) {
        return stockTraceService.traceBySerial(serial);
    }

    @GetMapping("/batch/{batchCode}")
    public java.util.List<StockTraceResult> byBatch(@PathVariable String batchCode) {
        return stockTraceService.traceByBatch(batchCode);
    }
}
