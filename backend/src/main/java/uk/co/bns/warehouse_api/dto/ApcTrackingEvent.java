package uk.co.bns.warehouse_api.dto;

// One scan/status event from APC's Tracks.json - dateTime is exactly as APC
// returns it (dd/MM/yyyy HH:mm:ss, e.g. "07/12/2023 15:06:55"), left as a
// string rather than parsed, since it's display-only here and APC's own
// format is unambiguous enough to sort and show as-is.
public record ApcTrackingEvent(String statusCode, String description, String dateTime, String location) {}
