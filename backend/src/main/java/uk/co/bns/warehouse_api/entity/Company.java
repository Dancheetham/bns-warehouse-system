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

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
