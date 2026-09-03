package uk.co.bns.warehouse_api.dto;

public record UnmatchedSkuSummary(String sku, int rowCount, int totalQty) {}
