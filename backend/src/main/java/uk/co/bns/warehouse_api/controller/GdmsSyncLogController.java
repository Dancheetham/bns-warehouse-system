package uk.co.bns.warehouse_api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.co.bns.warehouse_api.dto.GdmsSyncLogResponse;
import uk.co.bns.warehouse_api.service.GdmsSyncLogService;

import java.util.List;

/**
 * Powers the GDMS Sync Log page - a plain unfiltered list (search/date/
 * status filtering all happen client-side), matching every other log/history
 * page in this codebase (BugReports, PaymentTracking, InvoiceHistory).
 */
@RestController
@RequestMapping("/api/gdms-sync-log")
@RequiredArgsConstructor
public class GdmsSyncLogController {

    private final GdmsSyncLogService gdmsSyncLogService;

    @GetMapping
    public List<GdmsSyncLogResponse> getAll() {
        return gdmsSyncLogService.listAll();
    }
}
