package uk.co.bns.warehouse_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cartons")
@Getter
@Setter
@NoArgsConstructor
public class Carton {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "carton_number", nullable = false)
    private Integer cartonNumber;

    // Manual for now - summed from packed items' Product.weightKg as a starting
    // point on the frontend, but stored as an explicit override since a picker may
    // want to enter the actual scale reading instead.
    @Column(name = "weight_kg", precision = 10, scale = 3)
    private BigDecimal weightKg;

    // Set once despatch is confirmed and a (currently dummy) label is generated.
    @Column(name = "tracking_number")
    private String trackingNumber;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
