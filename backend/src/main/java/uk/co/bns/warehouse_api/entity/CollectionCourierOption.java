package uk.co.bns.warehouse_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One entry in the admin-managed list of "Collection" couriers offered on
 * the order screen when Courier = Collection - external services (a
 * customer's own courier, UKI, InXpress, Seabridge, ...) that BNS neither
 * books nor labels itself, just records which one was used. Orders
 * reference this by name (Order.collectionCourierName), not by id, so
 * renaming or deleting a row here never orphans a historical order.
 */
@Entity
@Table(name = "collection_courier_options")
@Getter
@Setter
@NoArgsConstructor
public class CollectionCourierOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
