package uk.co.bns.warehouse_api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uk.co.bns.warehouse_api.dto.StockImportPreview;
import uk.co.bns.warehouse_api.dto.StockImportResult;
import uk.co.bns.warehouse_api.service.StockImportService;

@RestController
@RequestMapping("/api/admin/stock-import")
@RequiredArgsConstructor
public class StockImportController {

    private final StockImportService stockImportService;

    @PostMapping("/preview")
    public StockImportPreview preview(@RequestParam("file") MultipartFile file) {
        return stockImportService.preview(file);
    }

    @PostMapping("/commit")
    public StockImportResult commit(@RequestParam("file") MultipartFile file, Authentication authentication) {
        return stockImportService.commit(file, authentication.getName());
    }
}
