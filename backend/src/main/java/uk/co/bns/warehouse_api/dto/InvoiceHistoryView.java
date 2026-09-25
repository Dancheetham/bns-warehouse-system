package uk.co.bns.warehouse_api.dto;

import uk.co.bns.warehouse_api.enums.InvoiceType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** One row on Invoice History - every invoice/credit note ever generated, not just outstanding ones. */
public record InvoiceHistoryView(
        Long invoiceId,
        Integer invoiceNumber,
        InvoiceType invoiceType,
        LocalDate generationDate,
        String companyName,
        BigDecimal netTotal,
        BigDecimal vatTotal,
        BigDecimal grandTotal,
        List<String> orderNumbers
) {}
