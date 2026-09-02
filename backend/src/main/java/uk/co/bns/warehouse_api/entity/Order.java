package uk.co.bns.warehouse_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.co.bns.warehouse_api.enums.OrderStatus;
import uk.co.bns.warehouse_api.enums.OrderType;
import uk.co.bns.warehouse_api.enums.PickingStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true)
    private String orderNumber;

    @Column(name = "order_date", nullable = false)
    private LocalDateTime orderDate;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_email")
    private String customerEmail;

    // Optional - links this order to a B2B credit account. Null for a normal
    // customer with no credit terms at all.
    @ManyToOne
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(name = "order_reference")
    private String orderReference;

    // Populated once orders start arriving from Shopify (or any other e-commerce
    // source) - null for orders entered directly against the current portal.
    @Column(name = "ecommerce_order_number")
    private String ecommerceOrderNumber;

    // Shopify's own GraphQL id (gid://shopify/Order/...), distinct from the
    // display name above - needed to push fulfillment status back precisely.
    @Column(name = "shopify_order_id")
    private String shopifyOrderId;

    @Column(name = "ordered_by")
    private String orderedBy;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.ON_HOLD;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false)
    private OrderType orderType = OrderType.ORDER;

    // Set once, on the order screen, as part of releasing an order for despatch -
    // replaces the old "set output method, print, come back, tick a box" flow.
    @Column(name = "shipping_cost", precision = 12, scale = 2)
    private java.math.BigDecimal shippingCost;

    @Column(name = "courier_method")
    private String courierMethod;

    // Free-text, order-level (not per-line) - shown on the picking note below the
    // line items, separated by a rule, so the warehouse team sees it without it
    // getting lost among individual product notes.
    @Column(name = "special_instructions", columnDefinition = "TEXT")
    private String specialInstructions;

    @Column(name = "acknowledgement_sent_at")
    private LocalDateTime acknowledgementSentAt;

    // Picking (handheld) tracking - separate from despatch/packing, which happens
    // afterwards on the web GUI once a pick is COMPLETE or PARTIAL.
    @Enumerated(EnumType.STRING)
    @Column(name = "picking_status", nullable = false)
    private PickingStatus pickingStatus = PickingStatus.NOT_STARTED;

    @Column(name = "picked_by")
    private String pickedBy;

    @Column(name = "picking_started_at")
    private LocalDateTime pickingStartedAt;

    @Column(name = "picking_completed_at")
    private LocalDateTime pickingCompletedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderLine> lines = new ArrayList<>();

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
