package uk.co.bns.warehouse_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.co.bns.warehouse_api.enums.TrackingType;

import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "tracking_type", nullable = false)
    private TrackingType trackingType = TrackingType.NONE;

    @Column(name = "default_password")
    private String defaultPassword;

    // The bin this product normally lives in - shown on picking notes so a
    // warehouse operator knows where to look first before checking alternatives.
    @ManyToOne
    @JoinColumn(name = "default_location_id")
    private Location defaultLocation;

    // Optional - nullable rather than defaulted to zero, so an unweighed product
    // doesn't silently understate a picking note's total weight. Used to calculate
    // the total weight line on the picking note.
    @Column(name = "weight_kg", precision = 10, scale = 3)
    private java.math.BigDecimal weightKg;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "shopify_product_id")
    private String shopifyProductId;

    @Column(name = "shopify_variant_id")
    private String shopifyVariantId;

    // True for anything a Shopify sync created - staff need to confirm the
    // tracking type (and default bin/password if relevant) before it's really
    // usable, since Shopify has no concept of those. Cleared automatically the
    // next time a human saves the product via the normal edit form.
    @Column(name = "needs_review", nullable = false)
    private Boolean needsReview = false;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
