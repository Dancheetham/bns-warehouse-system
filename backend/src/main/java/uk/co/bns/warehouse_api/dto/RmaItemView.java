package uk.co.bns.warehouse_api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RmaItemView(
        Long id,
        Long productId,
        String sku,
        String productName,
        String identifier,
        int quantity,
        boolean faulty,
        String grandstreamTicketNumber,
        String reasonForReturn,
        Long matchedOrderId,
        String matchedOrderNumber,
        BigDecimal matchedUnitPrice,
        LocalDate returnWindowExpiresAt,
        boolean returnWindowValid,
        boolean grandstreamWarrantyChecked,
        boolean received,
        boolean rsfApplied,
        boolean credited
) {}
