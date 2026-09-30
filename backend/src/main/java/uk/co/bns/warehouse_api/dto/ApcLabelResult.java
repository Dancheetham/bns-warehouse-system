package uk.co.bns.warehouse_api.dto;

// The decoded (Base64-stripped) label bytes APC returned for a waybill,
// plus whatever format string APC reported it in (e.g. "ZPL") - requested
// as ZPL so it can go through the same printRaw()/print-agent flow as DPD's
// labels.
public record ApcLabelResult(byte[] labelData, String format) {}
