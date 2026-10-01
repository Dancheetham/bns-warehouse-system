package uk.co.bns.warehouse_api.dto;

// One row on the "Available Services" admin page (Settings > Couriers > DPD/
// APC > Available Services) - every service code either courier has ever
// actually offered (live or cached - see DpdShippingService/ApcShippingService
// listAllKnownServicesForToggle()), with `enabled` reflecting whether it's
// currently allowed to appear in the order screen's Service dropdown.
// Unticking one here doesn't delete its record - it's just added to that
// courier's disabled-codes setting, so it can be ticked again later without
// losing the description - and never affects anything already booked using
// that code.
public record ServiceToggleOption(String code, String label, boolean enabled) {}
