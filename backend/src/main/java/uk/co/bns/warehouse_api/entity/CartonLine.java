package uk.co.bns.warehouse_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A quantity slice of an OrderLine, for packing. Every picked order line starts as
 * a single CartonLine covering its full quantityPicked, unassigned (carton == null).
 * "Split Line" and "Split Line by Quantity" divide a slice into smaller slices,
 * each still unassigned until packed into a Carton. The invariant that matters: the
 * CartonLines for a given order line should always sum to quantityPicked.
 */
@Entity
@Table(name = "carton_lines")
@Getter
@Setter
@NoArgsConstructor
public class CartonLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "order_line_id", nullable = false)
    private OrderLine orderLine;

    // Null = sitting in the unassigned pool, not yet packed into a carton.
    @ManyToOne
    @JoinColumn(name = "carton_id")
    private Carton carton;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
