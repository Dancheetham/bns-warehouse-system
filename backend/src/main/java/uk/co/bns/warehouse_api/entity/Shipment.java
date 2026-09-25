package uk.co.bns.warehouse_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A snapshot of one completed shipment on an order, written the moment that
 * shipment is about to be superseded by another - i.e. when a despatched
 * order is reopened for an extra shipment (added/increased lines on a
 * locked order - see OrderService.update()). Order itself only ever carries
 * ONE set of shipping/courier/DPD fields (shippingCost, courierMethod,
 * dpdNetworkKey, dpdShipmentId, dpdConsignmentNumber, dpdParcelNumbers,
 * dpdShippedAt) - those represent whichever shipment is current/in
 * progress right now. The moment a second (or third...) shipment starts,
 * whatever was on the order for the previous one is copied into a Shipment
 * row here first, and the order's own dpd* fields are cleared so the next
 * despatch confirmation books a genuinely new DPD shipment rather than
 * seeing one already booked and silently reusing it.
 *
 * Not written for an order's first-ever shipment - that one is still live
 * on the order itself (in progress or already Invoice Pending/Completed),
 * not superseded by anything yet, so there's nothing to archive.
 */
@Entity
@Table(name = "shipments")
@Getter
@Setter
@NoArgsConstructor
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // When this shipment actually went out - order.dpdShippedAt if it was
    // DPD-booked, otherwise falls back to order.despatchedAt at the time
    // (a manual/no-courier despatch has no dpdShippedAt of its own).
    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "courier_method")
    private String courierMethod;

    @Column(name = "dpd_network_key")
    private String dpdNetworkKey;

    @Column(name = "dpd_shipment_id")
    private String dpdShipmentId;

    @Column(name = "dpd_consignment_number")
    private String dpdConsignmentNumber;

    @Column(name = "dpd_parcel_numbers")
    private String dpdParcelNumbers;

    @Column(name = "shipping_cost")
    private BigDecimal shippingCost;

    // When this history row itself was written (i.e. when the NEXT
    // shipment was started) - distinct from shippedAt, which is when this
    // shipment itself actually went out.
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
