package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.bns.warehouse_api.dto.GdmsRunResult;
import uk.co.bns.warehouse_api.entity.Company;
import uk.co.bns.warehouse_api.entity.StockItem;
import uk.co.bns.warehouse_api.entity.StockMovement;
import uk.co.bns.warehouse_api.enums.MovementType;
import uk.co.bns.warehouse_api.repository.StockMovementRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The GDMS "end of day" workflow this replaces was manual: at the end of
 * each day, someone worked out what had gone out to GDMS-enabled customers
 * and assigned those devices' MACs to the right channel in GDMS by hand.
 * This does the same thing automatically - see the scheduled job below and
 * the manual triggers on OrderController/SettingsController that both call
 * into this.
 *
 * "What was despatched" is read from StockMovement (type DESPATCH) rather
 * than Order.despatchedAt - each despatched StockItem gets its own movement
 * row with an accurate createdAt the moment it's despatched
 * (DespatchService#confirmDespatch), whereas Order.despatchedAt is set once
 * and never updated if the same order ships again later (a re-opened or
 * multi-shipment order) - see gdms-api-findings.md for the fuller reasoning,
 * this was an explicit scoping decision.
 *
 * A StockItem is only ever assigned to GDMS once - gdmsSyncedAt is set the
 * moment a batch succeeds, so re-running this (the scheduled job on a day
 * that already had a manual run, or two manual runs back to back) never
 * re-sends an already-synced MAC.
 */
@Service
@RequiredArgsConstructor
public class GdmsEndOfDayService {

    private static final Logger log = LoggerFactory.getLogger(GdmsEndOfDayService.class);

    private final StockMovementRepository stockMovementRepository;
    private final GdmsChannelService gdmsChannelService;

    /**
     * Every day at 16:30 - the time BNS explicitly asked for, matching when
     * the old manual process used to be run (after the day's despatches are
     * done, but early enough that anyone here can still fix a problem before
     * going home).
     */
    @Scheduled(cron = "0 30 16 * * *")
    public void scheduledRun() {
        log.info("Running scheduled GDMS end-of-day channel assignment for {}", LocalDate.now());
        GdmsRunResult result = runForDate(LocalDate.now());
        log.info("GDMS end-of-day run complete: {} companies processed, {} devices assigned, {} skipped (no channel), {} error(s)",
                result.companiesProcessed(), result.devicesAssigned(), result.companiesSkippedNoChannel(), result.errors().size());
    }

    /** The global manual "run now" trigger on Settings > GDMS - everything despatched on the given date, across all companies. */
    @Transactional
    public GdmsRunResult runForDate(LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();
        List<StockMovement> movements = stockMovementRepository.findByMovementTypeAndCreatedAtBetween(MovementType.DESPATCH, from, to);
        return runForMovements(movements);
    }

    /** The per-order manual trigger in Sales Activity - only this order's despatched-but-unsynced items, regardless of when they went out. */
    @Transactional
    public GdmsRunResult runForOrder(Long orderId) {
        List<StockMovement> movements = stockMovementRepository.findByMovementTypeAndStockItem_OrderLine_Order_Id(MovementType.DESPATCH, orderId);
        return runForMovements(movements);
    }

    private GdmsRunResult runForMovements(List<StockMovement> movements) {
        // Group the despatched, not-yet-synced, GDMS-enabled-and-channelled
        // items by company, so each company's devices go to GDMS in one
        // (possibly batched) call rather than one call per device.
        //
        // One StockItem can legitimately have more than one DESPATCH movement
        // against it - e.g. despatched, then OrderReversalService#reverseToDespatch
        // put it back to ALLOCATED (which deliberately never touches
        // gdmsSyncedAt - see that class), then despatched again. Without
        // deduplicating by item here, that single physical device's MAC would
        // be added to the batch once per DESPATCH movement found, so GDMS (and
        // our own devicesAssigned count) would report it assigned multiple
        // times over for what's actually one device - confirmed happening on
        // a real test order (1 device despatched-then-redespatched during
        // testing, reported as "2 device(s) assigned").
        Map<Company, List<StockItem>> itemsByCompany = new LinkedHashMap<>();
        java.util.Set<Long> seenItemIds = new java.util.HashSet<>();
        int skippedNoChannel = 0;

        for (StockMovement movement : movements) {
            StockItem item = movement.getStockItem();
            if (item == null || item.getGdmsSyncedAt() != null) continue;
            if (item.getMacAddress() == null || item.getMacAddress().isBlank()) continue; // nothing to send GDMS for a serial-only item
            if (item.getOrderLine() == null || item.getOrderLine().getOrder() == null) continue;
            if (!seenItemIds.add(item.getId())) continue; // already queued from an earlier DESPATCH movement on this same item
            Company company = item.getOrderLine().getOrder().getCompany();
            if (company == null || !company.isGdms()) continue;
            if (company.getGdmsChannelId() == null || company.getGdmsChannelId().isBlank()) {
                skippedNoChannel++;
                continue;
            }
            itemsByCompany.computeIfAbsent(company, c -> new ArrayList<>()).add(item);
        }

        int devicesAssigned = 0;
        List<String> errors = new ArrayList<>();

        for (Map.Entry<Company, List<StockItem>> entry : itemsByCompany.entrySet()) {
            Company company = entry.getKey();
            List<StockItem> items = entry.getValue();
            List<String> macs = items.stream().map(StockItem::getMacAddress).toList();
            try {
                List<String> assignedMacs = gdmsChannelService.assignMacsToChannel(company.getGdmsChannelId(), macs);
                LocalDateTime now = LocalDateTime.now();
                for (StockItem item : items) {
                    if (assignedMacs.contains(item.getMacAddress())) {
                        item.setGdmsSyncedAt(now);
                        devicesAssigned++;
                    }
                }
            } catch (Exception e) {
                log.error("GDMS channel assignment failed for {}: {}", company.getName(), e.getMessage());
                errors.add(company.getName() + ": " + e.getMessage());
                // Left with gdmsSyncedAt still null - picked up cleanly by
                // the next run, no manual cleanup needed.
            }
        }

        return new GdmsRunResult(itemsByCompany.size(), devicesAssigned, skippedNoChannel, errors);
    }
}
