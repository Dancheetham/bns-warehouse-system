package uk.co.bns.warehouse_api.dto;

import java.util.List;

/**
 * Result of GET Tracks/{waybill}.json?searchtype=CarrierWaybill&history=Yes.
 * Unlike DPD (a plain public tracking-page link - see dpdTrackingUrl on the
 * frontend), APC has no equivalent unauthenticated consumer tracker that
 * reliably accepts a Hypaship WayBill, so this goes through APC's own
 * authenticated Tracks API instead and is rendered inside the app rather
 * than linked out to. `events` is every scan across every piece of the
 * shipment, newest first; `latestStatus`/`latestDateTime` are just
 * events.get(0)'s fields, pulled out separately so the frontend doesn't
 * have to know the list is pre-sorted to show a one-line summary.
 */
public record ApcTrackingResult(List<ApcTrackingEvent> events, String latestStatus, String latestDateTime) {}
