package uk.co.bns.warehouse_api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.dto.GdmsChannelLookupResult;
import uk.co.bns.warehouse_api.dto.GdmsRunResult;
import uk.co.bns.warehouse_api.service.DpdAuthService;
import uk.co.bns.warehouse_api.service.GdmsAuthService;
import uk.co.bns.warehouse_api.service.GdmsChannelService;
import uk.co.bns.warehouse_api.service.GdmsEndOfDayService;
import uk.co.bns.warehouse_api.service.SettingsService;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;
    private final DpdAuthService dpdAuthService;
    private final GdmsAuthService gdmsAuthService;
    private final GdmsChannelService gdmsChannelService;
    private final GdmsEndOfDayService gdmsEndOfDayService;

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

    /** Same idea as resetDpdConnection, for GDMS - drops the cached access/refresh token, forcing a fresh login next call. */
    @PostMapping("/gdms/reset-connection")
    public void resetGdmsConnection() {
        gdmsAuthService.resetConnection();
    }

    // Populates the GDMS channel dropdown on Companies.tsx - live lookup
    // against GDMS's /sub/list, falling back to the last successfully
    // fetched list if that call fails (see GdmsChannelService).
    @GetMapping("/gdms/channels")
    public GdmsChannelLookupResult gdmsChannels() {
        return gdmsChannelService.listChannels();
    }

    /**
     * The global "run GDMS end-of-day now" button on Settings > GDMS -
     * everything despatched on the given date (across every GDMS-enabled
     * company) that hasn't already been synced, run on demand instead of
     * waiting for the 16:30 scheduled job. Defaults to today when no date is
     * given. The optional date lets a whole bad day (GDMS down, or a batch
     * of stock despatched before Grandstream had assigned it to our channel
     * yet) be backfilled once things clear, rather than waiting on the next
     * scheduled run to pick up only *today's* despatches.
     */
    @PostMapping("/gdms/run-end-of-day")
    public GdmsRunResult runGdmsEndOfDay(@RequestParam(required = false) LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        String source = target.equals(LocalDate.now()) ? "Manual" : "Manual (backfill " + target + ")";
        return gdmsEndOfDayService.runForDate(target, source);
    }
}
