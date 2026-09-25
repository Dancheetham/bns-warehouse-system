package uk.co.bns.warehouse_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.co.bns.warehouse_api.enums.InvoiceType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One generated invoice or credit note (InvoiceService.generate) - a
 * standing record of what was actually sent, kept even if the underlying
 * orders/lines change later, so it stays an honest copy of what the
 * customer received. invoiceNumber is a single sequence shared by invoices
 * and credit notes alike (Settings > Invoicing > Next invoice number),
 * matching how the old OrderWise numbering worked.
 */
@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", nullable = false, unique = true)
    private Integer invoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_type", nullable = false)
    private InvoiceType invoiceType;

    @ManyToOne(optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // The date picked on the Generate Invoices page, not necessarily today -
    // shown on the PDF as the Invoice Date.
    @Column(name = "generation_date", nullable = false)
    private LocalDate generationDate;

    // Snapshot, not a live lookup through company - so this invoice still
    // reads correctly even if the company is later renamed.
    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "net_total", precision = 12, scale = 2, nullable = false)
    private BigDecimal netTotal;

    @Column(name = "vat_total", precision = 12, scale = 2, nullable = false)
    private BigDecimal vatTotal;

    @Column(name = "grand_total", precision = 12, scale = 2, nullable = false)
    private BigDecimal grandTotal;

    // The rate actually applied - snapshotted rather than re-read from
    // Company/Settings later, since either can change after the fact.
    @Column(name = "vat_rate", precision = 5, scale = 2, nullable = false)
    private BigDecimal vatRate;

    // Where the PDF was saved on disk (see InvoicePdfService) - null if it
    // somehow failed to save, though generation itself still succeeded.
    @Column(name = "pdf_path")
    private String pdfPath;

    @Column(name = "emailed_to")
    private String emailedTo;

    @Column(name = "email_sent_at")
    private LocalDateTime emailSentAt;

    // Set when the email genuinely failed (or there was no invoice_email on
    // file to send to) - the PDF still exists either way, this just means
    // nobody was actually notified automatically.
    @Column(name = "email_error", columnDefinition = "TEXT")
    private String emailError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private String createdBy;

    // Payment Tracking (PaymentTrackingService) - running total of Payments
    // (and applied credit-balance amounts) recorded against this invoice.
    // Kept as a stored running total rather than summed from Payments on
    // every read, since it's checked constantly (Payment Tracking's list,
    // the daily chaser job, credit-used calculations).
    @Column(name = "paid_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    // Set once each - the daily job never sends either chaser twice for the
    // same invoice. Null = not sent yet.
    @Column(name = "chaser_warning_sent_at")
    private LocalDateTime chaserWarningSentAt;

    @Column(name = "chaser_overdue_sent_at")
    private LocalDateTime chaserOverdueSentAt;

    // True once this invoice has caused its company to be auto-held - see
    // Company.autoHeld. Prevents the job re-holding a company over the same
    // invoice after a staff member has deliberately taken them off hold.
    @Column(name = "auto_hold_triggered", nullable = false)
    private boolean autoHoldTriggered = false;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceLine> lines = new ArrayList<>();

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
