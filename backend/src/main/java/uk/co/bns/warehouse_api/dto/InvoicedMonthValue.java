package uk.co.bns.warehouse_api.dto;

import java.math.BigDecimal;

/**
 * One month's worth of invoiced (order type ORDER) and credited (order type
 * CREDIT_REFUND) net value, for the "Invoiced Values by Month" dashboard
 * chart and export - mirrors the old OrderWise report of the same name.
 * month is 1-12.
 */
public record InvoicedMonthValue(int month, BigDecimal invoiceTotal, BigDecimal creditTotal) {}
