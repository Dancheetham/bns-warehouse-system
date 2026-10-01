package uk.co.bns.warehouse_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.dto.CollectionCourierOptionRequest;
import uk.co.bns.warehouse_api.dto.GdmsChannelLookupResult;
import uk.co.bns.warehouse_api.dto.GdmsRunResult;
import uk.co.bns.warehouse_api.dto.ServiceToggleOption;
import uk.co.bns.warehouse_api.entity.CollectionCourierOption;
import uk.co.bns.warehouse_api.service.ApcShippingService;
import uk.co.bns.warehouse_api.service.CollectionCourierOptionService;
import uk.co.bns.warehouse_api.service.DpdAuthService;
import uk.co.bns.warehouse_api.service.DpdShippingService;
import uk.co.bns.warehouse_api.service.GdmsAuthService;
import uk.co.bns.warehouse_api.service.GdmsChannelService;
import uk.co.bns.warehouse_api.service.GdmsEndOfDayService;
import uk.co.bns.warehouse_api.service.SettingsService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;
    private final DpdAuthService dpdAuthService;
    private final GdmsAuthService gdmsAuthService;
    private final GdmsChannelService gdmsChannelService;
    private final GdmsEndOfDayService gdmsEndOfDayService;
    private final CollectionCourierOptionService collectionCourierOptionService;
    private final DpdShippingService dpdShippingService;
    private final ApcShippingService apcShippingService;

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

    // Admin list/CRUD for the "Collection" courier list (Settings >
    // Couriers), including inactive rows - the order screen's own dropdown
    // reads the active-only /api/collection-courier-options endpoint
    // instead (CollectionCourierOptionController).
    @GetMapping("/collection-couriers")
    public List<CollectionCourierOption> collectionCouriers() {
        return collectionCourierOptionService.findAll();
    }

    @PostMapping("/collection-couriers")
    @ResponseStatus(HttpStatus.CREATED)
    public CollectionCourierOption createCollectionCourier(@Valid @RequestBody CollectionCourierOptionRequest request) {
        return collectionCourierOptionService.create(request);
    }

    @PutMapping("/collection-couriers/{id}")
    public CollectionCourierOption updateCollectionCourier(@PathVariable Long id, @Valid @RequestBody CollectionCourierOptionRequest request) {
        return collectionCourierOptionService.update(id, request);
    }

    @DeleteMapping("/collection-couriers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCollectionCourier(@PathVariable Long id) {
        collectionCourierOptionService.delete(id);
    }

    // "Available Services" admin pages (Settings > Couriers > DPD/APC) -
    // every service code either courier has ever actually offered, with a
    // tick to control whether it's still allowed to appear in the order
    // screen's Service dropdown. See DpdShippingService/ApcShippingService's
    // listAllKnownServicesForToggle()/setDisabledServices() for the full
    // reasoning - unticking never deletes anything, it's just added to a
    // disabled-codes setting that's filtered out at lookup time.
    @GetMapping("/dpd/available-services")
    public List<ServiceToggleOption> dpdAvailableServices() {
        return dpdShippingService.listAllKnownServicesForToggle();
    }

    @PutMapping("/dpd/available-services")
    public List<ServiceToggleOption> updateDpdAvailableServices(@RequestBody Set<String> disabledCodes) {
        dpdShippingService.setDisabledServices(disabledCodes);
        return dpdShippingService.listAllKnownServicesForToggle();
    }

    @GetMapping("/apc/available-services")
    public List<ServiceToggleOption> apcAvailableServices() {
        return apcShippingService.listAllKnownServicesForToggle();
    }

    @PutMapping("/apc/available-services")
    public List<ServiceToggleOption> updateApcAvailableServices(@RequestBody Set<String> disabledCodes) {
        apcShippingService.setDisabledServices(disabledCodes);
        return apcShippingService.listAllKnownServicesForToggle();
    }
}
