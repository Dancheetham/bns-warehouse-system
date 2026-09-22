package uk.co.bns.warehouse_api.dto;

import java.util.List;

/**
 * Response for the "Service" dropdown on the order screen. `live` is true
 * only when this list came straight from DPD's own live lookup for this
 * order's actual delivery address and weight just now. When that live call
 * fails, `services` falls back to the last list DPD returned successfully
 * (from any order, cached in Settings) rather than leaving the dropdown
 * empty - it's the set of services this account has genuinely offered
 * before, just not re-verified against this specific address/weight, so
 * `live` is false and `liveError` carries why the fresh check failed.
 */
public record DpdServiceLookupResult(List<DpdServiceOption> services, boolean live, String liveError) {}
