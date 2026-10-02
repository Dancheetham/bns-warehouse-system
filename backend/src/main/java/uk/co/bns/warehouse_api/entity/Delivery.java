package uk.co.bns.warehouse_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.co.bns.warehouse_api.enums.CourierType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One real, physical despatch event - written once per successful call to
 * DespatchService.confirmDespatch(), i.e. once per actual courier
 * consignment. An order despatched in two separate trips (partially
 * despatched, then the rest later) gets two of these, each with its own
 * deliveryNumber - that's the point: Order can have many Deliveries, so
 * staff can search/track by the specific consignment that went out, not
 * only by order number.
 *
 * Deliberately shipment-level only (Dan's call, 2026-10-02) - no link down
 * to individual StockItems/Cartons. The Delivery History detail page's
 * item/carton breakdown stays combined across the whole order; this just
 * records that a delivery happened and what it was.
 *
 * Deleted outright by OrderReversalService if the order is later reversed
 * (Reverse to Despatch / Cancel & Return to Stock) - see the reasoning
 * there. A row that still exists always represents a delivery that
 * genuinely, irreversibly went out.
 */
@Entity
@Table(name = "deliveries")
@Getter
@Setter
@NoArgsConstructor
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "delivery_number", nullable = false, unique = true)
    private String deliveryNumber;

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "despatched_at", nullable = false)
    private LocalDateTime despatchedAt;

    // True if this despatch left anything on the order still
    // outstanding (OrderStatus.PARTIALLY_DESPATCHED at the time) - shown in
    // Delivery History as "Part Shipped" so it's obvious at a glance this
    // wasn't the whole order.
    @Column(name = "partial", nullable = false)
    private boolean partial;

    @Enumerated(EnumType.STRING)
    @Column(name = "courier_type")
    private CourierType courierType;

    @Column(name = "courier_method")
    private String courierMethod;

    @Column(name = "collection_courier_name")
    private String collectionCourierName;

    // Whichever of DPD consignment / APC waybill / manual carton tracking
    // number applied to this specific despatch - same fallback order
    // DespatchService already uses for the Shopify fulfillment push.
    @Column(name = "consignment_number")
    private String consignmentNumber;

    @Column(name = "shipping_cost")
    private BigDecimal shippingCost;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
