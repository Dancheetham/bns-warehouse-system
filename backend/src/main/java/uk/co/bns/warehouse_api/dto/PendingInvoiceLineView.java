package uk.co.bns.warehouse_api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row on the Generate Invoices page - an order line still owed against
 * an INVOICE_PENDING order, with however much of it hasn't been invoiced
 * yet (a line can appear here more than once across separate Generate
 * Invoices runs if only part of it was ticked last time - see
 * OrderLine.quantityInvoiced).
 */
public record PendingInvoiceLineView(
        Long orderLineId,
        Long orderId,
        String orderNumber,
        LocalDateTime orderDate,
        Long companyId,
        String companyName,
        String sku,
        String description,
        int quantityPending,
        BigDecimal unitPrice,
        BigDecimal netAmount
) {}
