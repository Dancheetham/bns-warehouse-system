package uk.co.bns.warehouse_api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uk.co.bns.warehouse_api.dto.CompanyImportPreview;
import uk.co.bns.warehouse_api.dto.CompanyImportResult;
import uk.co.bns.warehouse_api.service.CompanyImportService;

@RestController
@RequestMapping("/api/admin/company-import")
@RequiredArgsConstructor
public class CompanyImportController {

    private final CompanyImportService companyImportService;

    @PostMapping("/preview")
    public CompanyImportPreview preview(@RequestParam("companiesFile") MultipartFile companiesFile,
                                         @RequestParam("contactsFile") MultipartFile contactsFile) {
        return companyImportService.preview(companiesFile, contactsFile);
    }

    @PostMapping("/commit")
    public CompanyImportResult commit(@RequestParam("companiesFile") MultipartFile companiesFile,
                                       @RequestParam("contactsFile") MultipartFile contactsFile) {
        return companyImportService.commit(companiesFile, contactsFile);
    }
}
