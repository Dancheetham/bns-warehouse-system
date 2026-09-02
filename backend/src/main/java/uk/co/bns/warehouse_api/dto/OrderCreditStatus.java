package uk.co.bns.warehouse_api.dto;

import java.math.BigDecimal;

/**
 * Shown as a banner whenever a linked order is opened, regardless of whether
 * it's currently over the limit - staff should always see the number, not
 * just when there's a problem.
 */
public record OrderCreditStatus(
        Long companyId,
        String companyName,
        BigDecimal creditLimit,
        BigDecimal creditUsed,
        BigDecimal creditAvailable,
        boolean overLimit,
        BigDecimal orderTotal,
        BigDecimal orderOutstanding
) {}
