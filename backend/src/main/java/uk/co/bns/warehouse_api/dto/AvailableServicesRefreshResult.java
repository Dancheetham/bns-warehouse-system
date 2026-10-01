package uk.co.bns.warehouse_api.dto;

import java.util.List;

// Result of "Refresh from live lookup" on the Available Services admin page
// (Settings > Couriers > DPD/APC) - triggers a sweep of live service-
// availability calls against a fixed set of representative UK-and-islands
// postcodes (see DpdShippingService/ApcShippingService's
// SERVICE_SWEEP_POSTCODES) with a nominal light weight, merging whatever
// comes back into the usual last-known-services cache, rather than waiting
// for real orders of every weight/destination to trickle through over time.
// `services` is the refreshed toggle list straight after the sweep;
// `warnings` names any probe postcode whose live call itself failed (e.g.
// a transient API error), so a single bad call doesn't silently hide that
// one region's services never got added.
public record AvailableServicesRefreshResult(List<ServiceToggleOption> services, List<String> warnings) {}
