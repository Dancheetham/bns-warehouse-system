package uk.co.bns.warehouse_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.co.bns.warehouse_api.enums.InvoiceGrouping;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "credit_limit", precision = 12, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "shopify_company_id")
    private String shopifyCompanyId;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // Used as the DPD customs "importer of record" (invoice.importerDetails)
    // when shipping to a customs country on this company's behalf - BNS is
    // the exporter/sender, but the receiving company is the importer, so its
    // own EORI/VAT belong here rather than in Settings alongside BNS's own.
    @Column(name = "eori_number")
    private String eoriNumber;

    @Column(name = "vat_number")
    private String vatNumber;

    // The OrderWise "Account number" code - kept as the match key for the
    // bulk company/contact importer (and any later Shopify sync), not
    // currently shown/edited anywhere else.
    @Column(name = "account_number")
    private String accountNumber;

    // Credit hold - stops us processing any more of this company's orders
    // until it's cleared. Distinct from the existing per-order
    // over-credit-limit block: this is a manual OrderWise-sourced flag, not
    // computed from the running balance.
    @Column(name = "on_hold", nullable = false)
    private boolean onHold = false;

    @Column(name = "do_not_use", nullable = false)
    private boolean doNotUse = false;

    // Tick-box columns carried over verbatim from the OrderWise export,
    // needed for a report to be built later.
    @Column(nullable = false)
    private boolean gaps = false;

    @Column(nullable = false)
    private boolean gdms = false;

    // Where Generate Invoices emails the PDF (InvoiceService) - separate from
    // any Contact's email since it's specifically who in the customer's
    // finance/AP team should receive invoices, which isn't always the same
    // person as the ordering contact. Null means Generate Invoices skips
    // this company (reported back, not silently dropped) until it's set.
    @Column(name = "invoice_email")
    private String invoiceEmail;

    // Overrides the global default VAT rate (Settings > Invoicing) for every
    // invoice/credit note raised against this company - e.g. 0 for a company
    // that's zero-rated (Irish B2B, reverse charge, etc). Null = use the
    // global default.
    @Column(name = "vat_rate", precision = 5, scale = 2)
    private BigDecimal vatRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_grouping", nullable = false)
    private InvoiceGrouping invoiceGrouping = InvoiceGrouping.PER_ORDER;

    // Payment Tracking (PaymentTrackingService/PaymentChaserService). Null =
    // use the global default (Settings > Invoicing > payment_terms_days).
    @Column(name = "payment_terms_days")
    private Integer paymentTermsDays;

    // If on, the daily job puts this company on hold the first time one of
    // its invoices goes past terms, and takes it back off once none remain
    // overdue - see autoHeld below for how a manual override is respected.
    @Column(name = "auto_hold_on_overdue", nullable = false)
    private boolean autoHoldOnOverdue = false;

    // True only while the current onHold=true was set by the scheduled job
    // itself, never by a person - CompanyService clears this on any manual
    // save of onHold, so a staff override of an auto-hold is never silently
    // reinstated the next time the job runs.
    @Column(name = "auto_held", nullable = false)
    private boolean autoHeld = false;

    // Unapplied credit from overpayments and non-auto-applied credit notes -
    // see PaymentTrackingService. Counted towards available credit straight
    // away (CompanyService.creditUsed), which is how available credit can
    // exceed the raw creditLimit once a customer's effectively paid ahead.
    @Column(name = "credit_balance", precision = 12, scale = 2, nullable = false)
    private BigDecimal creditBalance = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
