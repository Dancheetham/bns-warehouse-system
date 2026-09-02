package uk.co.bns.warehouse_api.dto;

public record RejectRmaRequest(String rejectedBy, String reason) {}
