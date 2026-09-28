package uk.co.bns.warehouse_api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.service.DpdAuthService;
import uk.co.bns.warehouse_api.service.SettingsService;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;
    private final DpdAuthService dpdAuthService;

    @GetMapping
    public Map<String, String> getAll() {
        return settingsService.getAll();
    }

    @PutMapping
    public Map<String, String> update(@RequestBody Map<String, String> values) {
        settingsService.setAll(values);
        return settingsService.getAll();
    }

    /**
     * Drops the cached DPD bearer/refresh tokens so the very next DPD API
     * call (label booking, tracking, etc.) re-authenticates from scratch
     * with the API key/secret rather than reusing the existing session -
     * for when DPD support asks us to "reset the connection" or get a new
     * bearer token after a change on their side. Does not touch the API
     * key/secret themselves.
     */
    @PostMapping("/dpd/reset-connection")
    public void resetDpdConnection() {
        dpdAuthService.resetConnection();
    }
}
