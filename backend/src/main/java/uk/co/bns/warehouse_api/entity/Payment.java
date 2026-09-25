package uk.co.bns.warehouse_api.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nullable now - a payment recorded from Payment Tracking links to the
    // Invoice below instead (an invoice can cover several orders once
    // Company.invoiceGrouping is CONSOLIDATED, so "which order" stopped being
    // well-defined). Payments recorded before Payment Tracking existed still
    // carry only this, kept for history.
    @ManyToOne
    @JoinColumn(name = "order_id")
    @JsonIgnoreProperties({"lines", "company"})
    private Order order;

    // Which invoice this payment (or applied credit-balance amount) pays off
    // - see PaymentTrackingService. Null for pre-Payment-Tracking history.
    @ManyToOne
    @JoinColumn(name = "invoice_id")
    @JsonIgnoreProperties({"lines", "company"})
    private Invoice invoice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    private String reference;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "recorded_by")
    private String recordedBy;

    // True when this "payment" is actually the company's own stored credit
    // balance being applied to an invoice, not new money coming in - kept
    // distinct so it's obvious on the invoice's history and never double-
    // counted as fresh income anywhere.
    @Column(name = "from_credit_balance", nullable = false)
    private boolean fromCreditBalance = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
