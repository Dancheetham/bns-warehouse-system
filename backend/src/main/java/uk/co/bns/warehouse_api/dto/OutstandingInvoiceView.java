package uk.co.bns.warehouse_api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One row on Payment Tracking - an INVOICE (never a credit note - those
 * don't get "chased") that still has something outstanding.
 * daysOverTerms is 0 until it's actually overdue, so the frontend can just
 * check &gt; 0 rather than comparing dates itself.
 */
public record OutstandingInvoiceView(
        Long invoiceId,
        Integer invoiceNumber,
        Long companyId,
        String companyName,
        LocalDate generationDate,
        BigDecimal grandTotal,
        BigDecimal paidAmount,
        BigDecimal outstanding,
        int daysSinceInvoiced,
        int paymentTermsDays,
        int daysOverTerms,
        List<String> orderNumbers
) {}
