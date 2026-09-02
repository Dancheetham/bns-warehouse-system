package uk.co.bns.warehouse_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.dto.BugReportResponse;
import uk.co.bns.warehouse_api.dto.CreateBugReportRequest;
import uk.co.bns.warehouse_api.service.BugReportService;

import java.util.List;

@RestController
@RequestMapping("/api/bug-reports")
@RequiredArgsConstructor
public class BugReportController {

    private final BugReportService bugReportService;

    @GetMapping
    public List<BugReportResponse> getAll() {
        return bugReportService.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BugReportResponse create(@Valid @RequestBody CreateBugReportRequest request) {
        return bugReportService.create(request);
    }
}
