package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.co.bns.warehouse_api.dto.BugReportResponse;
import uk.co.bns.warehouse_api.dto.CreateBugReportRequest;
import uk.co.bns.warehouse_api.entity.BugReport;
import uk.co.bns.warehouse_api.repository.BugReportRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BugReportService {

    private final BugReportRepository bugReportRepository;

    public BugReportResponse create(CreateBugReportRequest request) {
        BugReport report = new BugReport();
        report.setDescription(request.description());
        report.setErrorCode(request.errorCode());
        report.setContext(request.context());
        report.setSource(request.source() != null && !request.source().isBlank() ? request.source() : "MANUAL");
        bugReportRepository.save(report);
        return toResponse(report);
    }

    public List<BugReportResponse> listAll() {
        return bugReportRepository.findAllByOrderByOccurredAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    private BugReportResponse toResponse(BugReport r) {
        return new BugReportResponse(r.getId(), r.getOccurredAt(), r.getSource(), r.getErrorCode(), r.getDescription(), r.getContext());
    }
}
