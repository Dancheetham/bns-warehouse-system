package uk.co.bns.warehouse_api.dto;

import java.util.List;

public record ReceiveRmaRequest(String receivedBy, List<ReceiveRmaItemInput> items) {}
