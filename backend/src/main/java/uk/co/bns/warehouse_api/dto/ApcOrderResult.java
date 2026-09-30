package uk.co.bns.warehouse_api.dto;

// Result of booking an APC Overnight order (POST Orders.json). orderNumber
// is APC's own 18-digit order number; waybill is the 22-digit consignment
// id used both for label lookups and customer tracking.
public record ApcOrderResult(String orderNumber, String waybill) {}
