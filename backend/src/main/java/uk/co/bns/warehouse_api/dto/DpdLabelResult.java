package uk.co.bns.warehouse_api.dto;

// Raw label data exactly as DPD returned it (format determined by the
// Accept header sent when requesting it - ZPL by default here).
public record DpdLabelResult(String rawLabelData) {}
