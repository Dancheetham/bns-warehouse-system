package uk.co.bns.warehouse_api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RmaLookupResult(
        String identifier,
        boolean itemFound,
        boolean orderMatched,
        Long productId,
        String sku,
        String productName,
        Long orderId,
        String orderNumber,
        String orderDate,
        BigDecimal unitPrice,
        // Whichever window was asked for (faulty=true -> RTB warranty, false ->
        // non-faulty return window) - both configurable in Settings.
        LocalDate returnWindowExpiresAt,
        Boolean returnWindowValid,
        Integer returnWindowDays
) {}
