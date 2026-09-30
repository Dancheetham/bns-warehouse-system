package uk.co.bns.warehouse_api.dto;

// One APC Overnight product code offered on the order screen's Service
// dropdown when courierType == APC. Unlike DPD's live-looked-up services,
// this is a static list of the most common weekday product codes (see
// ApcShippingService.STANDARD_SERVICES) - APC's ServiceAvailability.json
// live lookup wasn't implemented for this first version, so the dropdown is
// paired with a free-text override on the order screen for anything not
// listed here, mirroring DPD's own free-text fallback pattern.
public record ApcServiceOption(String code, String description) {}
