package uk.co.bns.warehouse_api.dto;

import java.util.List;

/**
 * Summary of one GdmsEndOfDayService run (either the scheduled 16:30 job, the
 * global "run now" button, or a per-order manual trigger) - surfaced in the
 * UI so whoever ran it (or whoever checks the Settings page the next
 * morning) can see what actually happened without digging through logs.
 *
 * `errors` carries one line per company that failed part-way through (e.g.
 * GDMS rejected a batch) - those companies' items are simply left with
 * gdmsSyncedAt still null, ready to be picked up cleanly by the next run
 * rather than needing any manual cleanup.
 */
public record GdmsRunResult(
        int companiesProcessed,
        int devicesAssigned,
        int companiesSkippedNoChannel,
        List<String> errors
) {
    public static GdmsRunResult empty() {
        return new GdmsRunResult(0, 0, 0, List.of());
    }
}
