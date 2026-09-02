package uk.co.bns.warehouse_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.co.bns.warehouse_api.enums.RmaStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Replaces the emailed Excel RMA template. Submitted through the public form with
 * no login (there's no customer account system), matched best-effort against sales
 * history via StockItem.orderLine, and worked through a small pipeline:
 *
 *  SUBMITTED -> APPROVED (assigns rmaNumber; creates an ON_HOLD replacement order
 *               if any item is faulty) -> RECEIVED (physical return processed,
 *               creates one CREDIT_REFUND order covering everything credited)
 *          \-> REJECTED
 */
@Entity
@Table(name = "rma_requests")
@Getter
@Setter
@NoArgsConstructor
public class RmaRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_reference", nullable = false, unique = true)
    private String publicReference;

    @Column(name = "rma_number", unique = true)
    private String rmaNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RmaStatus status = RmaStatus.SUBMITTED;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_company")
    private String customerCompany;

    @Column(name = "customer_address", columnDefinition = "TEXT")
    private String customerAddress;

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "delivery_name")
    private String deliveryName;

    @Column(name = "delivery_town")
    private String deliveryTown;

    @Column(name = "delivery_country")
    private String deliveryCountry;

    @Column(name = "delivery_postcode")
    private String deliveryPostcode;

    @Column(name = "delivery_country_code")
    private String deliveryCountryCode;

    @ManyToOne
    @JoinColumn(name = "original_order_id")
    private Order originalOrder;

    @ManyToOne
    @JoinColumn(name = "replacement_order_id")
    private Order replacementOrder;

    @ManyToOne
    @JoinColumn(name = "credit_order_id")
    private Order creditOrder;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "rejected_by")
    private String rejectedBy;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "received_by")
    private String receivedBy;

    @OneToMany(mappedBy = "rmaRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RmaItem> items = new ArrayList<>();

    @PrePersist
    void prePersist() {
        this.submittedAt = LocalDateTime.now();
    }
}
