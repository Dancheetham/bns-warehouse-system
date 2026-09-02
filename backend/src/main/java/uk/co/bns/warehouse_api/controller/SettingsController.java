package uk.co.bns.warehouse_api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.service.SettingsService;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    public Map<String, String> getAll() {
        return settingsService.getAll();
    }

    @PutMapping
    public Map<String, String> update(@RequestBody Map<String, String> values) {
        settingsService.setAll(values);
        return settingsService.getAll();
    }
}
