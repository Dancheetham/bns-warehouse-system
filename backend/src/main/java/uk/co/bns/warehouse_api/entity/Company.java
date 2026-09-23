package uk.co.bns.warehouse_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
