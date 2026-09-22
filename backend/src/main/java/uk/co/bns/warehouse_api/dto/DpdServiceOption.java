package uk.co.bns.warehouse_api.dto;

/**
 * One DPD service actually available for a given order's delivery address
 * and weight right now, as returned by DPD's "validate outbound services"
 * lookup. networkKey is what gets sent back as the shipment's networkCode -
 * DPD's own docs say these "may change at any time and should not be
 * hardcoded", so this list is always fetched live, never stored statically.
 */
public record DpdServiceOption(String networkKey, String networkDesc, String serviceDesc) {}
