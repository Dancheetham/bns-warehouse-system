package uk.co.bns.warehouse_api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uk.co.bns.warehouse_api.dto.RestoreResult;
import uk.co.bns.warehouse_api.service.BackupService;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/backup")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;

    @GetMapping
    public ResponseEntity<byte[]> download() {
        byte[] zip = backupService.createBackupZip();
        String filename = "bns-warehouse-backup-" + LocalDate.now() + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(zip);
    }

    @PostMapping("/restore")
    public RestoreResult restore(@RequestParam("file") MultipartFile file) {
        return backupService.restoreZip(file);
    }
}
